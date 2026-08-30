package site.omagotchi.identityservice.auth.presentation.response;

import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;

public record PasswordChangeEmailOtpResponse(
        String challengeId,
        long expiresInSeconds
) {
    public static PasswordChangeEmailOtpResponse from(
            EmailVerificationChallengeResult result
    ) {
        return new PasswordChangeEmailOtpResponse(
                result.challengeId(),
                result.expiresInSeconds()
        );
    }
}
