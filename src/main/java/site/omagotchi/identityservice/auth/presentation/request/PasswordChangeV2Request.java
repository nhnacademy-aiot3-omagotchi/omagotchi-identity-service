package site.omagotchi.identityservice.auth.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PasswordChangeV2Request(
        @NotNull(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotNull(message = "새 비밀번호는 필수입니다.")
        String newPassword,

        @NotBlank(message = "Challenge ID는 필수입니다.")
        String challengeId,

        @NotBlank(message = "인증 코드는 필수입니다.")
        String code
) {

    @Override
    public String toString() {
        return "PasswordChangeV2Request[sensitive fields=[REDACTED]]";
    }
}
