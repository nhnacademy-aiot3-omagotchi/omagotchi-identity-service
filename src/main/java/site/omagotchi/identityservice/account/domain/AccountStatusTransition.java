package site.omagotchi.identityservice.account.domain;

import java.util.Objects;

public record AccountStatusTransition(
        AccountStatus before,
        AccountStatus after
) {

    public AccountStatusTransition {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    public static AccountStatusTransition unchanged(AccountStatus status) {
        return new AccountStatusTransition(status, status);
    }

    public static AccountStatusTransition changed(
            AccountStatus before,
            AccountStatus after
    ) {
        if (before == after) {
            throw new IllegalArgumentException("변경 전후 상태가 같으면 변경 전이로 기록할 수 없습니다.");
        }
        return new AccountStatusTransition(before, after);
    }

    public boolean changed() {
        return before != after;
    }
}
