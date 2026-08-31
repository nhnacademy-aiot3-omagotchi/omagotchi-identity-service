package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationUseService {

    private final EmailVerificationRepository repository;
    private final VerificationCodeAuthenticator codeAuthenticator;
    private final EmailVerificationProperties properties;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean verify(
            UUID challengeId,
            String normalizedEmail,
            EmailVerificationPurpose purpose,
            String code
    ) {
        EmailVerificationChallenge challenge = repository.lockChallenge(challengeId)
                .orElse(null);
        Instant now = clock.instant();

        if (challenge == null
                || !challenge.matchesContext(normalizedEmail, purpose)
                || !challenge.isUsableAt(now)) {
            return false;
        }

        boolean validFormat = code != null && code.matches("\\d{6}");
        boolean matched = validFormat && codeAuthenticator.matches(
                challenge.getCodeMac(),
                challengeId,
                normalizedEmail,
                purpose,
                code
        );
        if (!matched) {
            challenge.recordInvalidAttempt(properties.maximumFailedAttempts(), now);
            return false;
        }
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(UUID challengeId) {
        EmailVerificationChallenge challenge = repository.lockChallenge(challengeId)
                .orElseThrow(() -> new IllegalStateException("검증한 이메일 인증을 찾을 수 없습니다."));
        challenge.consume(clock.instant());
    }
}
