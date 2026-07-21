package site.omagotchi.identityservice.auth.presentation.dto;

import site.omagotchi.identityservice.auth.application.dto.LoginResult;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {

    public static TokenResponse from(LoginResult result) {
        return new TokenResponse(
                result.accessToken(),
                "Bearer",
                result.expiresInSeconds()
        );
    }
}
