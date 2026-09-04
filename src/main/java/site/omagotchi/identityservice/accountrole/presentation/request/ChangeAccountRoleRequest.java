package site.omagotchi.identityservice.accountrole.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import site.omagotchi.identityservice.accountrole.application.AdminGlobalRole;

public record ChangeAccountRoleRequest(

        @NotNull(message = "목표 전역 역할은 필수입니다.")
        TargetRole role,

        @NotBlank(message = "전역 역할 변경 사유는 필수입니다.")
        @Size(max = 500, message = "전역 역할 변경 사유는 500자 이하여야 합니다.")
        @Pattern(
                regexp = "^[^\\u0000]*$",
                message = "전역 역할 변경 사유에 NUL 문자를 포함할 수 없습니다."
        )
        String reason
) {

    public ChangeAccountRoleRequest {
        reason = reason == null ? null : reason.strip();
    }

    public AdminGlobalRole toAdminGlobalRole() {
        return switch (role) {
            case USER -> AdminGlobalRole.USER;
            case SYSTEM_ADMIN -> AdminGlobalRole.SYSTEM_ADMIN;
        };
    }

    public enum TargetRole {
        USER,
        SYSTEM_ADMIN
    }
}
