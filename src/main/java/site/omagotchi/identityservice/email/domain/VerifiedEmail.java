package site.omagotchi.identityservice.email.domain;

public record VerifiedEmail(
        String email,
        VerificationPurpose purpose
) {
}
