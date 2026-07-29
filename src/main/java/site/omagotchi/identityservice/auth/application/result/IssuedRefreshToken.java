package site.omagotchi.identityservice.auth.application.result;

import site.omagotchi.identityservice.auth.domain.RefreshToken;

public record IssuedRefreshToken(
        String value,
        RefreshToken refreshToken
) {

    // Token 원문의 로그 노출 방지
    @Override
    public String toString() {
        return "IssuedRefreshToken[value=[REDACTED]"
                + ", refreshToken=" + refreshToken.getId() + "]";
    }
}
