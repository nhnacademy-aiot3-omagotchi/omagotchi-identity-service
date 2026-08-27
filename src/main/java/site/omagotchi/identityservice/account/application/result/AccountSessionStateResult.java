package site.omagotchi.identityservice.account.application.result;

import site.omagotchi.identityservice.account.domain.Account;

import java.util.UUID;

public record AccountSessionStateResult(
        UUID accountId,
        String globalRole,
        AccountRefreshAccess refreshAccess
) {

    public static AccountSessionStateResult from(Account account) {
        return new AccountSessionStateResult(
                account.getId(),
                account.getGlobalRole().name(),
                AccountRefreshAccess.from(account.getStatus())
        );
    }
}
