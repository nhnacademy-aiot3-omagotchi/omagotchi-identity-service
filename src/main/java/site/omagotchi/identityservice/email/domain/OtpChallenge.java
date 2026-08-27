package site.omagotchi.identityservice.email.domain;

public record OtpChallenge(
        String challengeId,
        String code
) {
}
