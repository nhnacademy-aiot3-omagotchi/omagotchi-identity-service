package site.omagotchi.identityservice.auth.application.dto;

import java.time.Instant;

public record TokenIssueResult(
        String accessToken,
        long accessTokenExpiresInSeconds,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    // accessToken과 refreshToken이 의도치 않게 로그로 노출되는 것을 막는 방어 코드
    @Override
    public String toString() {
        return "TokenIssueResult[accessToken=[REDACTED], accessTokenExpiresInSeconds="
                + accessTokenExpiresInSeconds
                + ", refreshToken=[REDACTED], refreshTokenExpiresAt="
                + refreshTokenExpiresAt
                + "]";
    }
}

