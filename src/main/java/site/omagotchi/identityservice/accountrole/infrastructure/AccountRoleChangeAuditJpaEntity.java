package site.omagotchi.identityservice.accountrole.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAction;
import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAudit;
import site.omagotchi.identityservice.accountrole.domain.RecordedGlobalRole;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeReason;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_role_change_audits", schema = "identity_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRoleChangeAuditJpaEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "before_role", nullable = false, length = 20)
    private RecordedGlobalRole beforeRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_role", nullable = false, length = 20)
    private RecordedGlobalRole afterRole;

    @Column(nullable = false, length = AccountStatusChangeReason.MAX_LENGTH)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "request_id", length = 32)
    private String requestId;

    private AccountRoleChangeAuditJpaEntity(AccountRoleChangeAudit audit) {
        this.actorUserId = audit.getActorUserId();
        this.targetUserId = audit.getTargetUserId();
        this.action = audit.getAction();
        this.beforeRole = audit.getBeforeRole();
        this.afterRole = audit.getAfterRole();
        this.reason = audit.getReason();
        this.occurredAt = audit.getOccurredAt();
        this.requestId = audit.getRequestId();
    }

    public static AccountRoleChangeAuditJpaEntity from(AccountRoleChangeAudit audit) {
        return new AccountRoleChangeAuditJpaEntity(audit);
    }
}
