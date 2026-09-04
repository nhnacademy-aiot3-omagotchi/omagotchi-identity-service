package site.omagotchi.identityservice.accountrole.domain;

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

/**
 * 전역 역할 변경 감사 기록.
 *
 * <p>계정 상태 변경 감사와 사건 종류 및 조회 기준이 달라 테이블을 분리한다.</p>
 */
@Entity
@Table(name = "account_role_change_audits", schema = "identity_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRoleChangeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AccountRoleChangeAction action;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // 향후 요청 ID 연계를 위해 null을 허용하는 예약 필드
    @Column(name = "request_id", length = 32)
    private String requestId;

    private AccountRoleChangeAudit(
            UUID actorUserId,
            UUID targetUserId,
            AccountRoleChangeAction action,
            String reason,
            Instant occurredAt
    ) {
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.targetUserId = Objects.requireNonNull(targetUserId, "targetUserId");
        this.action = Objects.requireNonNull(action, "action");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.requestId = null;
    }

    public static AccountRoleChangeAudit create(
            UUID actorUserId,
            UUID targetUserId,
            AccountRoleChangeAction action,
            String reason,
            Instant occurredAt
    ) {
        return new AccountRoleChangeAudit(
                actorUserId,
                targetUserId,
                action,
                reason,
                occurredAt
        );
    }
}
