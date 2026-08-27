package site.omagotchi.identityservice.email.presentation.response;

import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;

public record EmailVerificationChallengeResponse(
        String challengeId,
        long expiresInSeconds
) {
    public static EmailVerificationChallengeResponse from(
            EmailVerificationChallengeResult result
    ) {
        return new EmailVerificationChallengeResponse(
                result.challengeId(),
                result.expiresInSeconds()
        );
    }
}
