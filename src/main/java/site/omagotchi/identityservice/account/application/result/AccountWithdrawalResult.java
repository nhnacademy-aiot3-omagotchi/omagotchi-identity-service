package site.omagotchi.identityservice.account.application.result;

import java.time.Instant;

public record AccountWithdrawalResult(
        boolean changed,
        Instant statusChangedAt
) {
}
