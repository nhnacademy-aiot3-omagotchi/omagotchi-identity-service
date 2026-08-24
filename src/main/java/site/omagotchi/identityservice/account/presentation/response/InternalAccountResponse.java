package site.omagotchi.identityservice.account.presentation.response;

import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;

import java.util.UUID;

public record InternalAccountResponse(
        UUID accountId,
        String displayName,
        Status status
) {

    public static InternalAccountResponse from(Account account) {
        return new InternalAccountResponse(
                account.getId(),
                account.getName(),
                Status.from(account.getStatus())
        );
    }

    public enum Status {
        ACTIVE,
        LOCKED,
        DISABLED,
        WITHDRAWN;

        private static Status from(AccountStatus status) {
            return switch (status) {
                case ACTIVE -> ACTIVE;
                case LOCKED -> LOCKED;
                case DISABLED -> DISABLED;
                case WITHDRAWN -> WITHDRAWN;
            };
        }
    }
}
