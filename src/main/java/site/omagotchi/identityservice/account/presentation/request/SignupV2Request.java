package site.omagotchi.identityservice.account.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignupV2Request(
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        // 누락만 요청 형식 오류로 처리하고 값의 정책 위반은 PasswordPolicy에서 분류
        @NotNull(message = "비밀번호는 필수입니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @NotBlank(message = "Challenge ID는 필수입니다.")
        String challengeId,

        @NotBlank(message = "인증 코드는 필수입니다.")
        String code
) {
    public SignupV2Request {
        email = email == null ? null : email.trim();
    }

    // 민감정보의 로그 노출 방지
    @Override
    public String toString() {
        return "SignupV2Request[sensitive fields=[REDACTED]]";
    }
}
