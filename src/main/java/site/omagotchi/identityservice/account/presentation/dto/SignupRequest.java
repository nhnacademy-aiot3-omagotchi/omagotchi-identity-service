package site.omagotchi.identityservice.account.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import site.omagotchi.identityservice.account.application.dto.SignupCommand;

public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        String email,

        @NotNull(message = "비밀번호는 필수입니다.")
        @Size(min = 15, max = 64, message = "비밀번호는 15자 이상 64자 이하여야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 30, message = "이름은 30자 이하여야 합니다.")
        String name
) {
    public SignupRequest {
        email = email == null ? null : email.trim();
    }

    public SignupCommand toCommand() {
        return new SignupCommand(email, password, name);
    }

    // 민감정보의 로그 노출 방지
    @Override
    public String toString() {
        return "SignupRequest[sensitive fields redacted]";
    }
}
