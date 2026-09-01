package site.omagotchi.identityservice.account.application.result;

import site.omagotchi.identityservice.account.domain.Account;

public record AccountRegistrationAttempt(
        boolean emailVerified,
        Account account
) {
    public static AccountRegistrationAttempt verificationFailed() {
        return new AccountRegistrationAttempt(false, null);
    }

    public static AccountRegistrationAttempt succeeded(Account account) {
        return new AccountRegistrationAttempt(true, account);
    }
}
