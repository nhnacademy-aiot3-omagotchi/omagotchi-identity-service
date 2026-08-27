package site.omagotchi.identityservice.account.presentation.request;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountRequest(

        @NotNull(message = "이름은 필수입니다.")
        String name
) {
}
