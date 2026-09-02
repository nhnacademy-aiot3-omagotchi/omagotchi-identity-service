package site.omagotchi.identityservice.accountrole.presentation.request;

import jakarta.validation.constraints.NotNull;

public record ChangeAccountRoleRequest(

        @NotNull(message = "목표 전역 역할은 필수입니다.")
        TargetRole role,

        String reason
) {

    public enum TargetRole {
        USER,
        SYSTEM_ADMIN
    }
}
