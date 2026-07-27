package site.omagotchi.identityservice.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {

    // 민감정보의 로그 노출 방지
    @Override
    public String toString() {
        return "LoginRequest[sensitive fields redacted]";
    }
}
