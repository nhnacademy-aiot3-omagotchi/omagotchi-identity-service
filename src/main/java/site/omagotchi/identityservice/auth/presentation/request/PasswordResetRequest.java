package site.omagotchi.identityservice.auth.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record PasswordResetRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotNull(message = "새 비밀번호는 필수입니다.")
        String newPassword,

        @NotNull(message = "이메일 인증 식별자는 필수입니다.")
        UUID challengeId,

        @NotNull(message = "이메일 인증번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "이메일 인증번호는 숫자 6자리여야 합니다.")
        String code
) {
    public PasswordResetRequest {
        email = email == null ? null : email.trim();
    }

    @Override
    public String toString() {
        return "PasswordResetRequest[sensitive fields redacted]";
    }
}
