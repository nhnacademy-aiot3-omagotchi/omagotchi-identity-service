package site.omagotchi.identityservice.account.presentation.response;

import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;

public record SignupEmailOtpResponse(
        String challengeId,
        long expiresInSeconds
) {
    public static SignupEmailOtpResponse from(
            EmailVerificationChallengeResult result
    ) {
        return new SignupEmailOtpResponse(
                result.challengeId(),
                result.expiresInSeconds()
        );
    }
}
