package site.omagotchi.identityservice.emailverification.presentation.response;

import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;

import java.util.UUID;

public record EmailVerificationResponse(
        UUID challengeId,
        long expiresInSeconds
) {
    public static EmailVerificationResponse from(IssuedEmailVerification issued) {
        return new EmailVerificationResponse(issued.challengeId(), issued.expiresInSeconds());
    }
}
