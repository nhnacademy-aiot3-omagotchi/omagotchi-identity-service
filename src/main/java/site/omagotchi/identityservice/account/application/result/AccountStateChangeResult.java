package site.omagotchi.identityservice.account.application.result;

import java.util.Objects;
import java.util.UUID;

public record AccountStateChangeResult(
        UUID targetAccountId,
        AccountStateValue before,
        AccountStateValue after
) {

    public AccountStateChangeResult {
        Objects.requireNonNull(targetAccountId, "targetAccountId");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    // 실제 상태 변경 여부
    public boolean changed() {
        return before != after;
    }

    // Refresh Session 폐기가 필요한 실제 비활성화 전이
    public boolean disabled() {
        return (before == AccountStateValue.ACTIVE || before == AccountStateValue.LOCKED)
                && after == AccountStateValue.DISABLED;
    }

    // 잠금 해제 감사 기록 대상
    public boolean unlocked() {
        return before == AccountStateValue.LOCKED
                && after == AccountStateValue.ACTIVE;
    }

    // 재활성화 감사 기록 대상
    public boolean reactivated() {
        return before == AccountStateValue.DISABLED
                && after == AccountStateValue.ACTIVE;
    }
}
