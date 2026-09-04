package site.omagotchi.identityservice.emailverification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "email_verification_scopes", schema = "identity_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationScope {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmailVerificationPurpose purpose;

    @Column(name = "active_challenge_id")
    private UUID activeChallengeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private EmailVerificationScope(
            UUID id,
            String email,
            EmailVerificationPurpose purpose,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        Instant normalizedCreatedAt = Objects.requireNonNull(createdAt, "createdAt").truncatedTo(ChronoUnit.MICROS);
        this.createdAt = normalizedCreatedAt;
        this.updatedAt = normalizedCreatedAt;
    }

    public static EmailVerificationScope create(
            UUID id,
            String email,
            EmailVerificationPurpose purpose,
            Instant createdAt
    ) {
        return new EmailVerificationScope(id, email, purpose, createdAt);
    }

    public void startChallenge(UUID challengeId, Instant now) {
        Instant startedAt = Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
        activeChallengeId = Objects.requireNonNull(challengeId, "challengeId");
        updatedAt = startedAt;
    }

    public boolean isCurrentChallenge(UUID challengeId) {
        return Objects.equals(activeChallengeId, challengeId);
    }
}
