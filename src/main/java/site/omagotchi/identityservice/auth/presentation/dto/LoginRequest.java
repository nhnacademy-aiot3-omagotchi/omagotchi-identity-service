package site.omagotchi.identityservice.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {

    // 인증 정보와 개인정보가 의도치 않게 로그로 노출되는 것을 막는 방어 코드
    @Override
    public String toString() {
        return "LoginRequest[sensitive fields redacted]";
    }
}
