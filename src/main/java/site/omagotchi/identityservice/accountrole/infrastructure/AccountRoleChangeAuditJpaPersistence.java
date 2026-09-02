package site.omagotchi.identityservice.accountrole.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.accountrole.application.port.AccountRoleChangeAuditRepository;
import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAudit;

@Repository
@RequiredArgsConstructor
public class AccountRoleChangeAuditJpaPersistence implements AccountRoleChangeAuditRepository {

    private final AccountRoleChangeAuditJpaRepository auditJpaRepository;

    @Override
    public void append(AccountRoleChangeAudit audit) {
        auditJpaRepository.save(AccountRoleChangeAuditJpaEntity.from(audit));
    }
}
