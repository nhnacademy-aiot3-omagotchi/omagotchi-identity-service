package site.omagotchi.identityservice.account.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.account.application.port.AccountStatusChangeAuditRepository;
import site.omagotchi.identityservice.account.domain.AccountStatusChangeAudit;

@Repository
@RequiredArgsConstructor
public class AccountStatusChangeAuditJpaPersistence implements AccountStatusChangeAuditRepository {

    private final AccountStatusChangeAuditJpaRepository auditJpaRepository;

    @Override
    public void append(AccountStatusChangeAudit audit) {
        auditJpaRepository.save(audit);
    }
}
