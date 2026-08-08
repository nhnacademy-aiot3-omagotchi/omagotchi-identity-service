package site.omagotchi.identityservice.auth.presentation.request;

public record RefreshTokenRequest(
        String refreshToken
) {

    // Refresh Token 원문의 로그 노출 방지
    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=[REDACTED]]";
    }
}
