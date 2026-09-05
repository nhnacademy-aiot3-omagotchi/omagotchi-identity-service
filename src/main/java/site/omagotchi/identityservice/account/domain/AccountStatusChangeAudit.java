package site.omagotchi.identityservice.account.domain;

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
import org.jspecify.annotations.Nullable;

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

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // HTTP 밖에서 발생한 사건까지 수용하는 선택적 요청 식별자
    @Column(name = "request_id", length = 32)
    private String requestId;

    private AccountStatusChangeAudit(
            UUID actorUserId,
            UUID targetUserId,
            AccountStatusChangeAction action,
            String reason,
            Instant occurredAt,
            @Nullable String requestId
    ) {
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.targetUserId = Objects.requireNonNull(targetUserId, "targetUserId");
        this.action = Objects.requireNonNull(action, "action");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.requestId = requestId;
    }

    public static AccountStatusChangeAudit create(
            UUID actorUserId,
            UUID targetUserId,
            AccountStatusChangeAction action,
            String reason,
            Instant occurredAt,
            @Nullable String requestId
    ) {
        return new AccountStatusChangeAudit(
                actorUserId,
                targetUserId,
                action,
                reason,
                occurredAt,
                requestId
        );
    }
}
