package site.omagotchi.identityservice.auth.presentation.dto;

import site.omagotchi.identityservice.auth.application.dto.TokenIssueResult;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {

    public static TokenResponse from(TokenIssueResult result) {
        return new TokenResponse(
                result.accessToken(),
                "Bearer",
                result.accessTokenExpiresInSeconds()
        );
    }
}
