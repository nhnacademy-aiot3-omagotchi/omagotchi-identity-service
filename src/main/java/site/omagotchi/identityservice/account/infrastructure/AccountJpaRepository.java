package site.omagotchi.identityservice.account.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.identityservice.account.domain.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT account
            FROM Account account
            WHERE account.email = :email
            """)
    Optional<Account> lockByEmail(@Param("email") String email);
}
