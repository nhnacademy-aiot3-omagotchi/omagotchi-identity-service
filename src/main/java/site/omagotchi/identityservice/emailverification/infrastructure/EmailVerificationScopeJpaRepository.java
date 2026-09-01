package site.omagotchi.identityservice.emailverification.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationScope;

import java.util.Optional;
import java.util.UUID;

interface EmailVerificationScopeJpaRepository
        extends JpaRepository<EmailVerificationScope, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT scope
            FROM EmailVerificationScope scope
            WHERE scope.email = :email AND scope.purpose = :purpose
            """)
    Optional<EmailVerificationScope> lockByEmailAndPurpose(
            @Param("email") String email,
            @Param("purpose") EmailVerificationPurpose purpose
    );
}
