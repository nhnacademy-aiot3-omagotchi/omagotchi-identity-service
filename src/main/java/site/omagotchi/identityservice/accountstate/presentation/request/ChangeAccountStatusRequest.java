package site.omagotchi.identityservice.accountstate.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import site.omagotchi.identityservice.accountstate.application.AdminAccountStatus;

public record ChangeAccountStatusRequest(

        @NotNull(message = "목표 계정 상태는 필수입니다.")
        TargetStatus status,

        @NotBlank(message = "계정 상태 변경 사유는 필수입니다.")
        @Size(max = 500, message = "계정 상태 변경 사유는 500자 이하여야 합니다.")
        @Pattern(
                regexp = "^[^\\u0000]*$",
                message = "계정 상태 변경 사유에 NUL 문자를 포함할 수 없습니다."
        )
        String reason
) {

    public ChangeAccountStatusRequest {
        reason = reason == null ? null : reason.strip();
    }

    public AdminAccountStatus toAdminAccountStatus() {
        return switch (status) {
            case ACTIVE -> AdminAccountStatus.ACTIVE;
            case DISABLED -> AdminAccountStatus.DISABLED;
        };
    }

    public enum TargetStatus {
        ACTIVE,
        DISABLED
    }
}
