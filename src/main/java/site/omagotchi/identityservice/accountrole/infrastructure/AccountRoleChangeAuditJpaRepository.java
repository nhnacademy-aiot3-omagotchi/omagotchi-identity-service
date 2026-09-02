package site.omagotchi.identityservice.accountrole.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRoleChangeAuditJpaRepository
        extends JpaRepository<AccountRoleChangeAuditJpaEntity, Long> {
}
