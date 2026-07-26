package site.omagotchi.identityservice.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", schema = "identity_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    private static final int SHA_256_HEX_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = SHA_256_HEX_LENGTH)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 40)
    private RefreshTokenRevocationReason revocationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private RefreshToken(
            UUID accountId,
            UUID familyId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.tokenHash = requireTokenHash(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Refresh Token 만료 시각은 생성 시각 이후여야 합니다.");
        }
    }

    public static RefreshToken issue(
            UUID accountId,
            UUID familyId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt
    ) {
        return new RefreshToken(accountId, familyId, tokenHash, expiresAt, createdAt);
    }

    public boolean isExpiredAt(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void markUsed(Instant now) {
        Instant usageTime = Objects.requireNonNull(now, "now");

        if (isUsed() || isRevoked() || isExpiredAt(usageTime)) {
            throw new IllegalStateException("사용할 수 없는 Refresh Token입니다.");
        }

        this.usedAt = usageTime;
    }

    private static String requireTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.length() != SHA_256_HEX_LENGTH) {
            throw new IllegalArgumentException("Refresh Token Hash는 SHA-256 Hex 형식이어야 합니다.");
        }
        return tokenHash;
    }
}
