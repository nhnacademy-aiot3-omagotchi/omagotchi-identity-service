package site.omagotchi.identityservice.accountstate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "account_status_change_audits", schema = "identity_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountStatusChangeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AccountStatusChangeAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_status", nullable = false, length = 20)
    private RecordedAccountStatus beforeStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_status", nullable = false, length = 20)
    private RecordedAccountStatus afterStatus;

    @Column(nullable = false, length = AccountStatusChangeReason.MAX_LENGTH)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // 향후 Request ID 연계를 위한 null 허용 예약 필드
    @Column(name = "request_id", length = 32)
    private String requestId;

    private AccountStatusChangeAudit(
            UUID actorUserId,
            UUID targetUserId,
            AccountStatusChangeAction action,
            RecordedAccountStatus beforeStatus,
            RecordedAccountStatus afterStatus,
            AccountStatusChangeReason reason,
            Instant occurredAt
    ) {
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.targetUserId = Objects.requireNonNull(targetUserId, "targetUserId");
        this.action = Objects.requireNonNull(action, "action");
        this.beforeStatus = Objects.requireNonNull(beforeStatus, "beforeStatus");
        this.afterStatus = Objects.requireNonNull(afterStatus, "afterStatus");
        this.reason = Objects.requireNonNull(reason, "reason").value();
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.requestId = null;
        // 잘못된 상태 전이의 감사 기록 생성 방지
        requireMatchingTransition();
    }

    public static AccountStatusChangeAudit record(
            UUID actorUserId,
            UUID targetUserId,
            AccountStatusChangeAction action,
            RecordedAccountStatus beforeStatus,
            RecordedAccountStatus afterStatus,
            AccountStatusChangeReason reason,
            Instant occurredAt
    ) {
        return new AccountStatusChangeAudit(
                actorUserId,
                targetUserId,
                action,
                beforeStatus,
                afterStatus,
                reason,
                occurredAt
        );
    }

    private void requireMatchingTransition() {
        boolean matches = switch (action) {
            case ACCOUNT_DISABLED ->
                    (beforeStatus == RecordedAccountStatus.ACTIVE
                            || beforeStatus == RecordedAccountStatus.LOCKED)
                            && afterStatus == RecordedAccountStatus.DISABLED;
            case ACCOUNT_UNLOCKED -> beforeStatus == RecordedAccountStatus.LOCKED
                    && afterStatus == RecordedAccountStatus.ACTIVE;
            case ACCOUNT_REACTIVATED -> beforeStatus == RecordedAccountStatus.DISABLED
                    && afterStatus == RecordedAccountStatus.ACTIVE;
        };
        if (!matches) {
            throw new IllegalArgumentException("감사 action과 계정 상태 전이가 일치하지 않습니다.");
        }
    }
}
