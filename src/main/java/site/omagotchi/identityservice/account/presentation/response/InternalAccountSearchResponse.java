package site.omagotchi.identityservice.account.presentation.response;

import site.omagotchi.identityservice.account.domain.Account;

import java.time.Instant;
import java.util.UUID;

public record InternalAccountSearchResponse(
        UUID accountId,
        String displayName,
        String email,
        InternalAccountResponse.Status status,
        Instant statusChangedAt
) {
    public static InternalAccountSearchResponse from(Account account) {
        InternalAccountResponse base = InternalAccountResponse.from(account);
        return new InternalAccountSearchResponse(
                base.accountId(),
                base.displayName(),
                account.getEmail(),
                base.status(),
                base.statusChangedAt()
        );
    }
}
