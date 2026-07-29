package site.omagotchi.identityservice.auth.application.result;

import java.time.Instant;

public record TokenIssueResult(
        String accessToken,
        long accessTokenExpiresInSeconds,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    // Token 원문의 로그 노출 방지
    @Override
    public String toString() {
        return "TokenIssueResult[accessToken=[REDACTED]"
                + ", accessTokenExpiresInSeconds=" + accessTokenExpiresInSeconds
                + ", refreshToken=[REDACTED]"
                + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
    }
}
