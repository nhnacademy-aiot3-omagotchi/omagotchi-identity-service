package site.omagotchi.identityservice.auth.presentation.response;

import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;

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

    // Access Token 원문의 로그 노출 방지
    @Override
    public String toString() {
        return "TokenResponse[accessToken=[REDACTED]"
                + ", tokenType=" + tokenType
                + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}
