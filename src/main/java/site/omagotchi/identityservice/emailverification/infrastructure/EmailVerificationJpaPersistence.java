package site.omagotchi.identityservice.emailverification.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.emailverification.domain.EmailDeliveryCooldown;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationScope;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EmailVerificationJpaPersistence implements EmailVerificationRepository {

    private final EntityManager entityManager;
    private final EmailDeliveryCooldownJpaRepository cooldownJpaRepository;
    private final EmailVerificationScopeJpaRepository scopeJpaRepository;
    private final EmailVerificationChallengeJpaRepository challengeJpaRepository;

    @Override
    public EmailDeliveryCooldown createIfAbsentAndLockCooldown(String email, Instant now) {
        Instant dbNow = now.truncatedTo(ChronoUnit.MICROS);
        entityManager.createNativeQuery("""
                        INSERT INTO identity_service.email_delivery_cooldowns (
                            email, active_challenge_id, next_issue_at, created_at, updated_at
                        )
                        VALUES (:email, NULL, :now, :now, :now)
                        ON CONFLICT (email) DO NOTHING
                        """)
                .setParameter("email", email)
                .setParameter("now", dbNow)
                .executeUpdate();

        return cooldownJpaRepository.lockByEmail(email)
                .orElseThrow(() -> new IllegalStateException("이메일 발송 쿨다운 잠금에 실패했습니다."));
    }

    @Override
    public EmailVerificationScope createIfAbsentAndLockScope(
            String email,
            EmailVerificationPurpose purpose,
            Instant now
    ) {
        Instant dbNow = now.truncatedTo(ChronoUnit.MICROS);
        entityManager.createNativeQuery("""
                        INSERT INTO identity_service.email_verification_scopes (
                            id, email, purpose, active_challenge_id, created_at, updated_at
                        )
                        VALUES (
                            :id, :email, :purpose, NULL, :now, :now
                        )
                        ON CONFLICT (email, purpose) DO NOTHING
                        """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("email", email)
                .setParameter("purpose", purpose.name())
                .setParameter("now", dbNow)
                .executeUpdate();

        return scopeJpaRepository.lockByEmailAndPurpose(email, purpose)
                .orElseThrow(() -> new IllegalStateException("이메일 인증 Scope 잠금에 실패했습니다."));
    }

    @Override
    public Optional<EmailVerificationChallenge> lockChallenge(UUID challengeId) {
        return challengeJpaRepository.lockById(challengeId);
    }

    @Override
    public EmailVerificationChallenge store(EmailVerificationChallenge challenge) {
        return challengeJpaRepository.save(challenge);
    }
}
