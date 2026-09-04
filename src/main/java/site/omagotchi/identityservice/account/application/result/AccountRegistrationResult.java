package site.omagotchi.identityservice.account.application.result;

import site.omagotchi.identityservice.account.domain.Account;

public record AccountRegistrationResult(
        Account account,
        Outcome outcome
) {
    public enum Outcome {
        CREATED,
        RECOVERED
    }
}
