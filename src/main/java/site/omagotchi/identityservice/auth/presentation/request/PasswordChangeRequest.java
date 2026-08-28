package site.omagotchi.identityservice.auth.presentation.request;

import jakarta.validation.constraints.NotNull;

public record PasswordChangeRequest(
        @NotNull(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotNull(message = "새 비밀번호는 필수입니다.")
        String newPassword
) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[currentPassword=[REDACTED], newPassword=[REDACTED]]";
    }
}
