package site.omagotchi.identityservice.accountstate.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeAudit;

public interface AccountStatusChangeAuditJpaRepository extends JpaRepository<AccountStatusChangeAudit, Long> {
}
