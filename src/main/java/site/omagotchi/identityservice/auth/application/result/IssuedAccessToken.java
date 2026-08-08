package site.omagotchi.identityservice.auth.application.result;

import java.time.Instant;

public record IssuedAccessToken(
        String value,
        Instant expiresAt
) {

    // Token 원문의 로그 노출 방지
    @Override
    public String toString() {
        return "IssuedAccessToken[value=[REDACTED]"
                + ", expiresAt=" + expiresAt + "]";
    }
}
