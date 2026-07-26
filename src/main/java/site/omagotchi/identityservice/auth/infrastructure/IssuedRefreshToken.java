package site.omagotchi.identityservice.auth.infrastructure;

import site.omagotchi.identityservice.auth.domain.RefreshToken;

public record IssuedRefreshToken(
        String value,
        RefreshToken refreshToken
) {

    // value가 의도치 않게 로그로 노출되는 것을 막는 방어 코드
    @Override
    public String toString() {
        return "IssuedRefreshToken[value=[REDACTED], refreshToken=" + refreshToken.getId() + "]";
    }
}
