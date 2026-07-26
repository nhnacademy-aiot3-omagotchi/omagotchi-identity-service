package site.omagotchi.identityservice.auth.application.dto;

public record LoginResult(
        String accessToken,
        long expiresInSeconds
) {
}
