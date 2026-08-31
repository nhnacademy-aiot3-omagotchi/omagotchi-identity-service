package site.omagotchi.identityservice.emailverification.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignupEmailOtpRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotNull(message = "비밀번호는 필수입니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String name
) {
    public SignupEmailOtpRequest {
        email = email == null ? null : email.trim();
    }

    @Override
    public String toString() {
        return "SignupEmailOtpRequest[sensitive fields redacted]";
    }
}
