package site.omagotchi.identityservice.auth.presentation.dto;

import site.omagotchi.identityservice.auth.application.dto.TokenIssueResult;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {

    private static final String TOKEN_TYPE = "Bearer";

    public static TokenResponse from(TokenIssueResult result) {
        return new TokenResponse(
                result.accessToken(),
                TOKEN_TYPE,
                result.accessTokenExpiresInSeconds()
        );
    }

    // accessToken이 의도치 않게 로그로 노출되는 것을 막는 방어 코드
    @Override
    public String toString() {
        return "TokenResponse[accessToken=[REDACTED], tokenType="
                + tokenType
                + ", expiresInSeconds="
                + expiresInSeconds
                + "]";
    }
}
