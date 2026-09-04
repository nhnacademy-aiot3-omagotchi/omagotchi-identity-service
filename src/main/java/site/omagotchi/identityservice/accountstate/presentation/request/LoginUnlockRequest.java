package site.omagotchi.identityservice.accountstate.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginUnlockRequest(
        @NotBlank(message = "로그인 잠금 해제 사유는 필수입니다.")
        @Size(max = 500, message = "로그인 잠금 해제 사유는 500자 이하여야 합니다.")
        @Pattern(
                regexp = "^[^\\u0000]*$",
                message = "로그인 잠금 해제 사유에 NUL 문자를 포함할 수 없습니다."
        )
        String reason
) {

    public LoginUnlockRequest {
        reason = reason == null ? null : reason.strip();
    }
}
