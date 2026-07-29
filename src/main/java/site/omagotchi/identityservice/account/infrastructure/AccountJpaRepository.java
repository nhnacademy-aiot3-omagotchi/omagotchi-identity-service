package site.omagotchi.identityservice.account.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.identityservice.account.domain.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByEmail(String email);
}
