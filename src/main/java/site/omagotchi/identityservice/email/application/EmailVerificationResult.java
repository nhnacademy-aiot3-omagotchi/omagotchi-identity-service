package site.omagotchi.identityservice.email.application;

public record EmailVerificationResult(
        String verificationToken,
        long expiresInSeconds
) {
}
