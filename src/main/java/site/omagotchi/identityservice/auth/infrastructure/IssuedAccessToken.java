package site.omagotchi.identityservice.auth.infrastructure;

public record IssuedAccessToken(
        String value,
        long expiresInSeconds
) {

    // value가 의도치 않게 로그로 노출되는 것을 막는 방어 코드
    @Override
    public String toString() {
        return "IssuedAccessToken[value=[REDACTED], expiresInSeconds=" + expiresInSeconds + "]";
    }
}
