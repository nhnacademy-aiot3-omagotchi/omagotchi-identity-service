package site.omagotchi.identityservice.accountstate.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.accountstate.application.port.AccountStatusChangeAuditRepository;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeAudit;

@Repository
@RequiredArgsConstructor
public class AccountStatusChangeAuditJpaPersistence implements AccountStatusChangeAuditRepository {

    private final AccountStatusChangeAuditJpaRepository auditJpaRepository;

    @Override
    public void append(AccountStatusChangeAudit audit) {
        auditJpaRepository.save(audit);
    }
}
