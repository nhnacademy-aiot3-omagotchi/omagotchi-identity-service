package site.omagotchi.identityservice.accountstate.presentation.request;

import jakarta.validation.constraints.NotNull;

public record SelfAccountWithdrawalRequest(

        @NotNull(message = "현재 비밀번호는 필수입니다.")
        String currentPassword
) {

    @Override
    public String toString() {
        return "SelfAccountWithdrawalRequest[currentPassword=[REDACTED]]";
    }
}
