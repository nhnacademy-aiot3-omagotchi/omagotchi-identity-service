package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.emailverification.application.result.PreparedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationScope;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationIssuanceTransaction {

    private final EmailVerificationRepository repository;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationCodeAuthenticator codeAuthenticator;
    private final EmailVerificationProperties properties;
    private final Clock clock;

    @Transactional
    public PreparedEmailVerification prepare(
            String normalizedEmail,
            EmailVerificationPurpose purpose
    ) {
        Instant scopeInitializationAt = now();
        EmailVerificationScope scope = repository.createIfAbsentAndLockScope(
                normalizedEmail,
                purpose,
                scopeInitializationAt
        );
        Instant now = now();

        if (!scope.canIssueAt(now)) {
            throw new EmailVerificationCooldownException(scope.retryAfterSecondsAt(now));
        }

        if (scope.getActiveChallengeId() != null) {
            repository.lockChallenge(scope.getActiveChallengeId())
                    .ifPresent(challenge -> challenge.supersede(now));
        }

        UUID challengeId = UUID.randomUUID();
        String code = codeGenerator.generate();
        Instant expiresAt = now.plus(properties.codeTtl());
        String codeMac = codeAuthenticator.encode(
                challengeId,
                normalizedEmail,
                purpose,
                code
        );

        EmailVerificationChallenge challenge = EmailVerificationChallenge.issue(
                challengeId,
                scope.getId(),
                normalizedEmail,
                purpose,
                codeMac,
                expiresAt,
                now
        );
        repository.store(challenge);
        scope.startChallenge(challengeId, now, properties.cooldown());

        return new PreparedEmailVerification(
                challengeId,
                normalizedEmail,
                purpose,
                code,
                expiresAt
        );
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
