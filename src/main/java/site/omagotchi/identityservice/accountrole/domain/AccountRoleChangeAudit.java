package site.omagotchi.identityservice.accountrole.domain;

import lombok.Getter;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeReason;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 전역 역할 변경 감사 기록.
 *
 * <p>사유 규칙은 계정 상태 변경과 같으므로 {@link AccountStatusChangeReason}을 재사용한다.
 * 전이 규칙은 다르므로 테이블과 CHECK는 분리한다.</p>
 */
@Getter
public class AccountRoleChangeAudit {

    private final UUID actorUserId;
    private final UUID targetUserId;
    private final AccountRoleChangeAction action;
    private final RecordedGlobalRole beforeRole;
    private final RecordedGlobalRole afterRole;
    private final String reason;
    private final Instant occurredAt;

    // 향후 Request ID 연계를 위한 null 허용 예약 필드
    private final String requestId;

    private AccountRoleChangeAudit(
            UUID actorUserId,
            UUID targetUserId,
            AccountRoleChangeAction action,
            RecordedGlobalRole beforeRole,
            RecordedGlobalRole afterRole,
            AccountStatusChangeReason reason,
            Instant occurredAt
    ) {
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.targetUserId = Objects.requireNonNull(targetUserId, "targetUserId");
        this.action = Objects.requireNonNull(action, "action");
        this.beforeRole = Objects.requireNonNull(beforeRole, "beforeRole");
        this.afterRole = Objects.requireNonNull(afterRole, "afterRole");
        this.reason = Objects.requireNonNull(reason, "reason").value();
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.requestId = null;
        // 잘못된 역할 전이의 감사 기록 생성 방지
        requireMatchingTransition();
    }

    public static AccountRoleChangeAudit record(
            UUID actorUserId,
            UUID targetUserId,
            AccountRoleChangeAction action,
            RecordedGlobalRole beforeRole,
            RecordedGlobalRole afterRole,
            AccountStatusChangeReason reason,
            Instant occurredAt
    ) {
        return new AccountRoleChangeAudit(
                actorUserId,
                targetUserId,
                action,
                beforeRole,
                afterRole,
                reason,
                occurredAt
        );
    }

    private void requireMatchingTransition() {
        boolean matches = switch (action) {
            case ROLE_GRANTED -> beforeRole == RecordedGlobalRole.USER
                    && afterRole == RecordedGlobalRole.SYSTEM_ADMIN;
            case ROLE_REVOKED -> beforeRole == RecordedGlobalRole.SYSTEM_ADMIN
                    && afterRole == RecordedGlobalRole.USER;
        };
        if (!matches) {
            throw new IllegalArgumentException("감사 action과 전역 역할 전이가 일치하지 않습니다.");
        }
    }
}
