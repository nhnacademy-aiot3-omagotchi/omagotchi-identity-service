package site.omagotchi.identityservice.account.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.identityservice.account.domain.AccountStatusChangeAudit;

public interface AccountStatusChangeAuditJpaRepository extends JpaRepository<AccountStatusChangeAudit, Long> {
}
