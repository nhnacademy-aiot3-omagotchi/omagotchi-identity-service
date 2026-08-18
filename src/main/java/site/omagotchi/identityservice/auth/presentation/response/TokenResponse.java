package site.omagotchi.identityservice.auth.presentation.response;

import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;

import java.time.Instant;
import java.util.UUID;

public record TokenResponse(
        UUID userId,
        String globalRole,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    public static TokenResponse from(TokenIssueResult result) {
        return new TokenResponse(
                result.userId(),
                result.globalRole(),
                result.accessToken(),
                result.accessTokenExpiresAt(),
                result.refreshToken(),
                result.refreshTokenExpiresAt()
        );
    }

    // Token 원문의 로그 노출 방지
    @Override
    public String toString() {
        return "TokenResponse[userId=" + userId
                + ", globalRole=" + globalRole
                + ", accessToken=[REDACTED]"
                + ", accessTokenExpiresAt=" + accessTokenExpiresAt
                + ", refreshToken=[REDACTED]"
                + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
    }
}
