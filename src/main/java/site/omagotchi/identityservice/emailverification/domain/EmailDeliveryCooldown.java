package site.omagotchi.identityservice.emailverification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "email_delivery_cooldowns", schema = "identity_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailDeliveryCooldown {

    @Id
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "active_challenge_id")
    private UUID activeChallengeId;

    @Column(name = "next_issue_at", nullable = false)
    private Instant nextIssueAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private EmailDeliveryCooldown(String email, Instant createdAt) {
        this.email = requireEmail(email);
        Instant normalizedCreatedAt = normalize(createdAt, "createdAt");
        this.nextIssueAt = normalizedCreatedAt;
        this.createdAt = normalizedCreatedAt;
        this.updatedAt = normalizedCreatedAt;
    }

    public static EmailDeliveryCooldown create(String email, Instant createdAt) {
        return new EmailDeliveryCooldown(email, createdAt);
    }

    public boolean canIssueAt(Instant now) {
        Instant checkedAt = normalize(now, "now");
        return !checkedAt.isBefore(nextIssueAt);
    }

    public long retryAfterSecondsAt(Instant now) {
        Instant checkedAt = normalize(now, "now");
        if (canIssueAt(checkedAt)) {
            return 0;
        }

        long remainingMillis = Duration.between(checkedAt, nextIssueAt).toMillis();
        return Math.max(1, (remainingMillis + 999) / 1_000);
    }

    public void reserve(UUID challengeId, Instant now, Duration cooldown) {
        Instant reservedAt = normalize(now, "now");
        Duration requiredCooldown = Objects.requireNonNull(cooldown, "cooldown");
        if (!canIssueAt(reservedAt)) {
            throw new IllegalStateException("쿨다운 중에는 새 이메일 인증을 시작할 수 없습니다.");
        }
        if (requiredCooldown.isZero() || requiredCooldown.isNegative()) {
            throw new IllegalArgumentException("이메일 인증 쿨다운은 0보다 커야 합니다.");
        }

        activeChallengeId = Objects.requireNonNull(challengeId, "challengeId");
        nextIssueAt = reservedAt.plus(requiredCooldown);
        updatedAt = reservedAt;
    }

    public boolean isReservedBy(UUID challengeId) {
        return Objects.equals(activeChallengeId, challengeId);
    }

    public void releaseIfReservedBy(UUID challengeId, Instant now) {
        if (!isReservedBy(Objects.requireNonNull(challengeId, "challengeId"))) {
            return;
        }

        Instant releasedAt = normalize(now, "now");
        nextIssueAt = releasedAt;
        updatedAt = releasedAt;
    }

    private static String requireEmail(String email) {
        String requiredEmail = Objects.requireNonNull(email, "email");
        if (requiredEmail.isBlank()) {
            throw new IllegalArgumentException("이메일은 비어 있을 수 없습니다.");
        }
        return requiredEmail;
    }

    private static Instant normalize(Instant instant, String name) {
        return Objects.requireNonNull(instant, name).truncatedTo(ChronoUnit.MICROS);
    }
}
