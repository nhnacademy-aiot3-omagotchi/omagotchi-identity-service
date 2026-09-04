package site.omagotchi.identityservice.emailverification.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.identityservice.emailverification.domain.EmailDeliveryCooldown;

import java.util.Optional;

interface EmailDeliveryCooldownJpaRepository
        extends JpaRepository<EmailDeliveryCooldown, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT cooldown
            FROM EmailDeliveryCooldown cooldown
            WHERE cooldown.email = :email
            """)
    Optional<EmailDeliveryCooldown> lockByEmail(@Param("email") String email);
}
