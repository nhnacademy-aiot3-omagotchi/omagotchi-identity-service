package site.omagotchi.identityservice.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "accounts", schema = "identity_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "global_role", nullable = false, length = 20)
    private GlobalRole globalRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private short failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Account(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.globalRole = GlobalRole.USER;
        this.status = AccountStatus.ACTIVE;
        this.failedLoginAttempts = 0;
    }

    public static Account register(String email, String passwordHash, String name) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = normalize(name);

        if (normalizedEmail.isEmpty()
                || normalizedEmail.length() > 254
                || passwordHash == null
                || passwordHash.isBlank()
                || normalizedName.isEmpty()
                || normalizedName.length() > 30
        ) {
            throw new BusinessException(AccountErrorCode.INVALID_SIGNUP_INPUT);
        }

        return new Account(normalizedEmail, passwordHash, normalizedName);
    }

    public boolean isLoginAllowed() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isRefreshAllowed() {
        return status == AccountStatus.ACTIVE
                || status == AccountStatus.LOCKED;
    }

    public static String normalizeEmail(String email) {
        return normalizeLowercase(email);
    }

    private static String normalizeLowercase(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
