package site.omagotchi.identityservice.account.application.result;

import site.omagotchi.identityservice.account.domain.Account;

import java.util.UUID;

public record AccountAuthenticationResult(
        UUID accountId,
        String globalRole
) {

    public static AccountAuthenticationResult from(Account account) {
        return new AccountAuthenticationResult(
                account.getId(),
                account.getGlobalRole().name()
        );
    }
}
