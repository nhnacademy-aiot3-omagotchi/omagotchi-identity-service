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
@Table(name = "email_verification_challenges", schema = "identity_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationChallenge {

    private static final int HMAC_SHA_256_HEX_LENGTH = 64;

    @Id
    private UUID id;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmailVerificationPurpose purpose;

    @Column(name = "code_mac", nullable = false, length = HMAC_SHA_256_HEX_LENGTH)
    private String codeMac;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailVerificationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private EmailDeliveryStatus deliveryStatus;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private EmailVerificationChallenge(
            UUID id,
            UUID scopeId,
            String email,
            EmailVerificationPurpose purpose,
            String codeMac,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.scopeId = Objects.requireNonNull(scopeId, "scopeId");
        this.email = Objects.requireNonNull(email, "email");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.codeMac = requireCodeMac(codeMac);
        Instant normalizedExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt").truncatedTo(ChronoUnit.MICROS);
        Instant normalizedCreatedAt = Objects.requireNonNull(createdAt, "createdAt").truncatedTo(ChronoUnit.MICROS);
        this.expiresAt = normalizedExpiresAt;
        this.createdAt = normalizedCreatedAt;
        this.updatedAt = normalizedCreatedAt;
        this.status = EmailVerificationStatus.OPEN;
        this.deliveryStatus = EmailDeliveryStatus.PENDING;
        this.failedAttempts = 0;

        if (!normalizedExpiresAt.isAfter(normalizedCreatedAt)) {
            throw new IllegalArgumentException("이메일 인증 만료 시각은 생성 시각 이후여야 합니다.");
        }
    }

    public static EmailVerificationChallenge issue(
            UUID id,
            UUID scopeId,
            String email,
            EmailVerificationPurpose purpose,
            String codeMac,
            Instant expiresAt,
            Instant createdAt
    ) {
        return new EmailVerificationChallenge(
                id,
                scopeId,
                email,
                purpose,
                codeMac,
                expiresAt,
                createdAt
        );
    }

    public boolean matchesContext(String expectedEmail, EmailVerificationPurpose expectedPurpose) {
        return email.equals(expectedEmail) && purpose == expectedPurpose;
    }

    public boolean isUsableAt(Instant now) {
        Instant checkedAt = Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
        return status == EmailVerificationStatus.OPEN
                && checkedAt.isBefore(expiresAt);
    }

    public void recordInvalidAttempt(int maximumFailedAttempts, Instant now) {
        if (maximumFailedAttempts < 1 || maximumFailedAttempts > Short.MAX_VALUE) {
            throw new IllegalArgumentException("최대 이메일 인증 실패 횟수 범위가 올바르지 않습니다.");
        }
        Instant recordedAt = Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
        if (!isUsableAt(recordedAt)) {
            throw new IllegalStateException("사용할 수 없는 이메일 인증에는 실패를 기록할 수 없습니다.");
        }

        int nextAttempts = failedAttempts + 1;
        failedAttempts = (short) Math.min(nextAttempts, maximumFailedAttempts);
        if (nextAttempts >= maximumFailedAttempts) {
            status = EmailVerificationStatus.EXHAUSTED;
        }
        updatedAt = recordedAt;
    }

    public void consume(Instant now) {
        Instant consumedAt = Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
        if (!isUsableAt(consumedAt)) {
            throw new IllegalStateException("사용할 수 없는 이메일 인증은 소비할 수 없습니다.");
        }
        status = EmailVerificationStatus.CONSUMED;
        updatedAt = consumedAt;
    }

    public void supersede(Instant now) {
        if (status != EmailVerificationStatus.OPEN) {
            return;
        }
        status = EmailVerificationStatus.SUPERSEDED;
        updatedAt = Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
    }

    public void markDeliveryAccepted(Instant now) {
        if (deliveryStatus != EmailDeliveryStatus.PENDING) {
            return;
        }
        deliveryStatus = EmailDeliveryStatus.ACCEPTED;
        updatedAt = Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
    }

    public void markDeliveryFailed(Instant now) {
        if (deliveryStatus != EmailDeliveryStatus.PENDING) {
            return;
        }
        deliveryStatus = EmailDeliveryStatus.FAILED;
        updatedAt = Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
    }

    private static String requireCodeMac(String codeMac) {
        if (codeMac == null || codeMac.length() != HMAC_SHA_256_HEX_LENGTH) {
            throw new IllegalArgumentException("인증번호 MAC은 HMAC-SHA256 Hex 형식이어야 합니다.");
        }
        return codeMac;
    }
}
