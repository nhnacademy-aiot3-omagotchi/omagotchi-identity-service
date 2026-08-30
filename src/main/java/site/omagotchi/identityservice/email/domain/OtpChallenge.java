package site.omagotchi.identityservice.email.domain;

public record OtpChallenge(
        String challengeId,
        String code
) {

    @Override
    public String toString() {
        return "OtpChallenge[sensitive fields=[REDACTED]]";
    }
}
