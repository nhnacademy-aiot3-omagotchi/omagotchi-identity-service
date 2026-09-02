package site.omagotchi.identityservice.accountaudit.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.accountaudit.application.port.AccountPermissionChangeAuditPage;
import site.omagotchi.identityservice.accountaudit.application.port.AccountPermissionChangeAuditRepository;
import site.omagotchi.identityservice.accountaudit.domain.AccountPermissionChangeAudit;

@Repository
@RequiredArgsConstructor
public class AccountPermissionChangeAuditJpaPersistence
        implements AccountPermissionChangeAuditRepository {

    /*
     * occurred_at 은 같은 트랜잭션에서 나온 두 감사(역할 변경 + 비활성화)가 같은 Clock
     * 값을 갖는다. Tie-breaker 없이 정렬하면 페이지 경계에서 행이 중복되거나 누락된다.
     * 원본 구분과 원본 PK 까지 내려가야 전순서가 된다.
     */
    private static final Sort RECENT_FIRST = Sort.by(
            Sort.Order.desc("occurredAt"),
            Sort.Order.desc("id.auditType"),
            Sort.Order.desc("id.sourceId")
    );

    private final AccountPermissionChangeAuditJpaRepository auditJpaRepository;

    @Override
    public AccountPermissionChangeAuditPage findRecent(int page, int size) {
        Page<AccountPermissionChangeAudit> found = auditJpaRepository.findAll(
                PageRequest.of(page, size, RECENT_FIRST)
        );
        return new AccountPermissionChangeAuditPage(
                found.getContent(),
                found.getTotalElements()
        );
    }
}
