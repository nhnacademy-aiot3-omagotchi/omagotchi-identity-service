package site.omagotchi.identityservice.auth.infrastructure;

public record IssuedAccessToken(
        String value,
        long expiresInSeconds
) {

    // Token 원문의 로그 노출 방지
    @Override
    public String toString() {
        return "IssuedAccessToken[value=[REDACTED]"
                + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}
