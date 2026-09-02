package site.omagotchi.identityservice.accountaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * 계정 상태·전역 역할 감사의 통합 조회 모델.
 *
 * <p>V7 뷰에 매핑한다. {@link Immutable} 로 표시해 Dirty Checking 대상에서 빼고,
 * 실수로 쓰기 경로가 붙어도 Hibernate 가 UPDATE 를 만들지 않게 한다. 감사는 원본
 * 테이블에만 append 된다.</p>
 */
@Entity
@Immutable
@Table(name = "account_permission_change_audits", schema = "identity_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountPermissionChangeAudit {

    @EmbeddedId
    private AccountPermissionChangeAuditId id;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "target_user_id", nullable = false, updatable = false)
    private UUID targetUserId;

    @Column(nullable = false, updatable = false, length = 40)
    private String action;

    // 상태 감사는 AccountStatus, 역할 감사는 GlobalRole 값이 들어온다.
    // 뷰에서 합치는 순간 두 enum 의 합집합이라 문자열로 둔다.
    @Column(name = "before_value", nullable = false, updatable = false, length = 20)
    private String beforeValue;

    @Column(name = "after_value", nullable = false, updatable = false, length = 20)
    private String afterValue;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public AccountPermissionChangeAuditType getAuditType() {
        return id.getAuditType();
    }
}
