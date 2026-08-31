package site.omagotchi.identityservice.emailverification.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;

import java.util.Optional;
import java.util.UUID;

interface EmailVerificationChallengeJpaRepository
        extends JpaRepository<EmailVerificationChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT challenge
            FROM EmailVerificationChallenge challenge
            WHERE challenge.id = :challengeId
            """)
    Optional<EmailVerificationChallenge> lockById(@Param("challengeId") UUID challengeId);
}
