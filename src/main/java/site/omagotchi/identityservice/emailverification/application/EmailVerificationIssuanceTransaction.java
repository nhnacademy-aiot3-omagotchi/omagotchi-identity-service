package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.emailverification.application.result.PreparedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailDeliveryCooldown;
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
        Instant initializationAt = currentTime();

        EmailDeliveryCooldown cooldown = repository.createIfAbsentAndLockCooldown(
                normalizedEmail,
                initializationAt
        );

        EmailVerificationScope scope = repository.createIfAbsentAndLockScope(
                normalizedEmail,
                purpose,
                initializationAt
        );
        Instant cooldownCheckedAt = currentTime();

        if (!cooldown.canIssueAt(cooldownCheckedAt)) {
            throw new EmailVerificationCooldownException(
                    cooldown.retryAfterSecondsAt(cooldownCheckedAt)
            );
        }

        EmailVerificationChallenge previousChallenge = null;
        if (scope.getActiveChallengeId() != null) {
            previousChallenge = repository.lockChallenge(scope.getActiveChallengeId())
                    .orElse(null);
        }

        Instant issuedAt = currentTime();
        if (previousChallenge != null) {
            previousChallenge.supersede(issuedAt);
        }

        UUID challengeId = UUID.randomUUID();
        String code = codeGenerator.generate();
        Instant expiresAt = issuedAt.plus(properties.codeTtl());
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
                issuedAt
        );
        repository.store(challenge);
        scope.startChallenge(challengeId, issuedAt);
        cooldown.reserve(challengeId, issuedAt, properties.cooldown());

        return new PreparedEmailVerification(
                challengeId,
                normalizedEmail,
                purpose,
                code,
                expiresAt
        );
    }

    /** 현재 시각을 PostgreSQL 저장 정밀도에 맞춰 반환한다. */
    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
