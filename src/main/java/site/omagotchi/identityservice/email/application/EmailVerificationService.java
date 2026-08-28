package site.omagotchi.identityservice.email.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.domain.EmailPolicy;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String CODE_PATTERN = "\\d{6}";

    private final EmailVerificationRepository emailVerificationRepository;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationMailDispatchService mailDispatchService;
    private final EmailVerificationProperties properties;

    public EmailVerificationChallengeResult requestCode(
            String email,
            VerificationPurpose purpose
    ) {
        String normalizedEmail = requireValidEmail(email);
        VerificationPurpose requiredPurpose = Objects.requireNonNull(purpose, "purpose");

        boolean acquired = emailVerificationRepository.acquireCooldown(
                requiredPurpose,
                normalizedEmail,
                properties.resendCooldown()
        );
        if (!acquired) {
            throw new EmailVerificationCooldownException(
                    emailVerificationRepository.remainingCooldownSeconds(
                            requiredPurpose,
                            normalizedEmail
                    )
            );
        }

        String challengeId = UUID.randomUUID().toString();
        long expiresInSeconds = Math.max(1, properties.codeTtl().toSeconds());
        String verificationCode = codeGenerator.generate();
        emailVerificationRepository.replaceChallenge(
                requiredPurpose,
                normalizedEmail,
                new OtpChallenge(challengeId, verificationCode),
                properties.codeTtl()
        );

        try {
            mailDispatchService.dispatch(
                    requiredPurpose,
                    normalizedEmail,
                    verificationCode,
                    challengeId,
                    properties.codeTtl()
            );
        } catch (RuntimeException exception) {
            emailVerificationRepository.deleteChallengeIfMatches(
                    requiredPurpose,
                    normalizedEmail,
                    challengeId
            );
            throw exception;
        }
        return new EmailVerificationChallengeResult(challengeId, expiresInSeconds);
    }

    public void verifyAndConsumeCode(
            String email,
            VerificationPurpose purpose,
            String challengeId,
            String verificationCode
    ) {
        String normalizedEmail = requireValidEmail(email);
        VerificationPurpose requiredPurpose = Objects.requireNonNull(purpose, "purpose");

        if (challengeId == null || challengeId.isBlank()
                || verificationCode == null
                || !verificationCode.matches(CODE_PATTERN)) {
            throw invalidVerification();
        }

        OtpVerificationStatus status = emailVerificationRepository.verifyAndConsume(
                requiredPurpose,
                normalizedEmail,
                challengeId,
                verificationCode,
                properties.maximumAttempts()
        );
        if (status != OtpVerificationStatus.VERIFIED) {
            throw invalidVerification();
        }
    }

    private String requireValidEmail(String email) {
        if (!EmailPolicy.isSatisfiedBy(email)) {
            throw new BusinessException(EmailVerificationErrorCode.INVALID_EMAIL);
        }
        return EmailPolicy.normalize(email);
    }

    private BusinessException invalidVerification() {
        return new BusinessException(EmailVerificationErrorCode.INVALID);
    }
}
