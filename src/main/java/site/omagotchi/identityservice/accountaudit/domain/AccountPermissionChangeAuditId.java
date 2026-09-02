package site.omagotchi.identityservice.accountaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 통합 감사 뷰의 식별자.
 *
 * <p>source_id 는 원본 테이블별 PK 라 뷰 전체에서는 중복된다. 단일 컬럼을 @Id 로 쓰면
 * 1차 캐시가 상태 감사 1번과 역할 감사 1번을 같은 행으로 오인한다. 그래서 원본 구분을
 * 식별자에 포함한다.</p>
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountPermissionChangeAuditId implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_type", nullable = false, length = 20)
    private AccountPermissionChangeAuditType auditType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;
}
