package site.omagotchi.identityservice.account.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.identityservice.account.domain.Account;

import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);
}
