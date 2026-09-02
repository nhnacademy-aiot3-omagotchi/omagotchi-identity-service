package site.omagotchi.identityservice.account.application.result;

import site.omagotchi.identityservice.account.domain.GlobalRole;

import java.util.Objects;
import java.util.UUID;

/** 전역 역할 변경 결과. 상태 변경의 {@link AccountStateChangeResult}와 같은 역할을 한다. */
public record AccountRoleChangeResult(
        UUID targetAccountId,
        GlobalRole before,
        GlobalRole after
) {
    public AccountRoleChangeResult {
        Objects.requireNonNull(targetAccountId, "targetAccountId");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    // 실제 역할 변경 여부. 같은 역할 요청은 후속 처리를 건너뛴다.
    public boolean changed() {
        return before != after;
    }

    // 관리자 권한 부여 감사 기록 대상
    public boolean granted() {
        return before == GlobalRole.USER && after == GlobalRole.SYSTEM_ADMIN;
    }

    // 관리자 권한 회수 감사 기록 대상
    public boolean revoked() {
        return before == GlobalRole.SYSTEM_ADMIN && after == GlobalRole.USER;
    }
}
