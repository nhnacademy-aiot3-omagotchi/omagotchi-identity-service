package site.omagotchi.identityservice.accountstate.presentation.request;

import jakarta.validation.constraints.NotNull;

public record ChangeAccountStatusRequest(

        @NotNull(message = "목표 계정 상태는 필수입니다.")
        TargetStatus status,

        String reason
) {

    public enum TargetStatus {
        ACTIVE,
        DISABLED
    }
}
