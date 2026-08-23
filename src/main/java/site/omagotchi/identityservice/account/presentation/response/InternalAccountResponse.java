package site.omagotchi.identityservice.account.presentation.response;

import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;

import java.util.UUID;

public record InternalAccountResponse(
        UUID accountId,
        String displayName,
        AccountStatus status
) {

    public static InternalAccountResponse from(Account account) {
        return new InternalAccountResponse(
                account.getId(),
                account.getName(),
                account.getStatus()
        );
    }
}
