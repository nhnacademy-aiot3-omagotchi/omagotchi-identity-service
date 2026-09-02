package site.omagotchi.identityservice.accountrole.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAudit;

public interface AccountRoleChangeAuditJpaRepository
        extends JpaRepository<AccountRoleChangeAudit, Long> {
}
