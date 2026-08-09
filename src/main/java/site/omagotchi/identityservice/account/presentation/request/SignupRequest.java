package site.omagotchi.identityservice.account.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        // 누락만 요청 형식 오류로 처리하고 값의 정책 위반은 PasswordPolicy에서 분류
        @NotNull(message = "비밀번호는 필수입니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String name
) {
    public SignupRequest {
        email = email == null ? null : email.trim();
    }

    // 민감정보의 로그 노출 방지
    @Override
    public String toString() {
        return "SignupRequest[sensitive fields redacted]";
    }
}
