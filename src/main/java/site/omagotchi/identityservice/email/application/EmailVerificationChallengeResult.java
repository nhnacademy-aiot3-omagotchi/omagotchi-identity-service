package site.omagotchi.identityservice.email.application;

public record EmailVerificationChallengeResult(
        String challengeId,
        long expiresInSeconds
) {
}
