package site.omagotchi.identityservice.email.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Locale;
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
            String normalizedEmail,
            VerificationPurpose purpose
    ) {
        String requiredEmail = requireNormalizedEmail(normalizedEmail);
        VerificationPurpose requiredPurpose = Objects.requireNonNull(purpose, "purpose");

        boolean acquired = emailVerificationRepository.acquireCooldown(
                requiredPurpose,
                requiredEmail,
                properties.resendCooldown()
        );
        if (!acquired) {
            throw new EmailVerificationCooldownException(
                    emailVerificationRepository.remainingCooldownSeconds(
                            requiredPurpose,
                            requiredEmail
                    )
            );
        }

        String challengeId = UUID.randomUUID().toString();
        long expiresInSeconds = Math.max(1, properties.codeTtl().toSeconds());
        String verificationCode = codeGenerator.generate();
        emailVerificationRepository.replaceChallenge(
                requiredPurpose,
                requiredEmail,
                new OtpChallenge(challengeId, verificationCode),
                properties.codeTtl()
        );

        try {
            mailDispatchService.dispatch(
                    requiredPurpose,
                    requiredEmail,
                    verificationCode,
                    challengeId,
                    properties.codeTtl()
            );
        } catch (RuntimeException exception) {
            emailVerificationRepository.deleteChallengeIfMatches(
                    requiredPurpose,
                    requiredEmail,
                    challengeId
            );
            throw exception;
        }
        return new EmailVerificationChallengeResult(challengeId, expiresInSeconds);
    }

    public void verifyAndConsumeCode(
            String normalizedEmail,
            VerificationPurpose purpose,
            String challengeId,
            String verificationCode
    ) {
        String requiredEmail = requireNormalizedEmail(normalizedEmail);
        VerificationPurpose requiredPurpose = Objects.requireNonNull(purpose, "purpose");

        if (challengeId == null || challengeId.isBlank()
                || verificationCode == null
                || !verificationCode.matches(CODE_PATTERN)) {
            throw invalidVerification();
        }

        OtpVerificationStatus status = emailVerificationRepository.verifyAndConsume(
                requiredPurpose,
                requiredEmail,
                challengeId,
                verificationCode,
                properties.maximumAttempts()
        );
        if (status != OtpVerificationStatus.VERIFIED) {
            throw invalidVerification();
        }
    }

    private String requireNormalizedEmail(String email) {
        String requiredEmail = Objects.requireNonNull(email, "normalizedEmail");
        if (requiredEmail.isBlank()
                || !requiredEmail.equals(requiredEmail.trim())
                || !requiredEmail.equals(requiredEmail.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("normalizedEmail은 정규화된 값이어야 합니다.");
        }
        return requiredEmail;
    }

    private BusinessException invalidVerification() {
        return new BusinessException(EmailVerificationErrorCode.INVALID);
    }
}
