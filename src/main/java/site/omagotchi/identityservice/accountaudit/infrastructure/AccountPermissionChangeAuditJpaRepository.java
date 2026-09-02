package site.omagotchi.identityservice.accountaudit.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.identityservice.accountaudit.domain.AccountPermissionChangeAudit;
import site.omagotchi.identityservice.accountaudit.domain.AccountPermissionChangeAuditId;

public interface AccountPermissionChangeAuditJpaRepository
        extends JpaRepository<AccountPermissionChangeAudit, AccountPermissionChangeAuditId> {
}
