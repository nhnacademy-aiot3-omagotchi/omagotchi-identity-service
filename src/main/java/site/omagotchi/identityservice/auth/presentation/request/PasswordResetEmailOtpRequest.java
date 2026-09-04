package site.omagotchi.identityservice.auth.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetEmailOtpRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        String email
) {
    public PasswordResetEmailOtpRequest {
        email = email == null ? null : email.trim();
    }

    @Override
    public String toString() {
        return "PasswordResetEmailOtpRequest[sensitive fields redacted]";
    }
}
