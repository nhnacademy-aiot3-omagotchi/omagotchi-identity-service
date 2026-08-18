package site.omagotchi.identityservice.auth.application.result;

import java.time.Instant;
import java.util.UUID;

public record TokenIssueResult(
        UUID userId,
        String globalRole,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    // Token 원문의 로그 노출 방지
    @Override
    public String toString() {
        return "TokenIssueResult[userId=" + userId
                + ", globalRole=" + globalRole
                + ", accessToken=[REDACTED]"
                + ", accessTokenExpiresAt=" + accessTokenExpiresAt
                + ", refreshToken=[REDACTED]"
                + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
    }
}
