package site.omagotchi.identityservice.accountstate.presentation.response;

import java.time.Instant;

public record SelfAccountWithdrawalResponse(
        Instant recoveryDeadline
) {
}
