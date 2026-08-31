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

import java.time.Duration;
import java.time.Instant;
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

    @Column(name = "next_issue_at", nullable = false)
    private Instant nextIssueAt;

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
        this.nextIssueAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static EmailVerificationScope create(
            UUID id,
            String email,
            EmailVerificationPurpose purpose,
            Instant createdAt
    ) {
        return new EmailVerificationScope(id, email, purpose, createdAt);
    }

    public boolean canIssueAt(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(nextIssueAt);
    }

    public long retryAfterSecondsAt(Instant now) {
        Instant checkedAt = Objects.requireNonNull(now, "now");
        if (canIssueAt(checkedAt)) {
            return 0;
        }

        long remainingMillis = Duration.between(checkedAt, nextIssueAt).toMillis();
        return Math.max(1, (remainingMillis + 999) / 1_000);
    }

    public void startChallenge(UUID challengeId, Instant now, Duration cooldown) {
        Instant startedAt = Objects.requireNonNull(now, "now");
        Duration requiredCooldown = Objects.requireNonNull(cooldown, "cooldown");
        if (!canIssueAt(startedAt)) {
            throw new IllegalStateException("쿨다운 중에는 새 이메일 인증을 시작할 수 없습니다.");
        }
        if (requiredCooldown.isZero() || requiredCooldown.isNegative()) {
            throw new IllegalArgumentException("이메일 인증 쿨다운은 0보다 커야 합니다.");
        }

        activeChallengeId = Objects.requireNonNull(challengeId, "challengeId");
        nextIssueAt = startedAt.plus(requiredCooldown);
        updatedAt = startedAt;
    }

    public boolean isCurrentChallenge(UUID challengeId) {
        return Objects.equals(activeChallengeId, challengeId);
    }

    public void releaseCooldownForCurrentChallenge(UUID challengeId, Instant now) {
        if (!isCurrentChallenge(Objects.requireNonNull(challengeId, "challengeId"))) {
            return;
        }

        Instant releasedAt = Objects.requireNonNull(now, "now");
        nextIssueAt = releasedAt;
        updatedAt = releasedAt;
    }
}
