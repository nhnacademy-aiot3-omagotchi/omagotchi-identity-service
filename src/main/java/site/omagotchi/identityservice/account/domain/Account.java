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

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
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
        String normalizedEmail = EmailPolicy.normalize(email);
        String normalizedName = normalizeName(name);

        if (!isNormalizedRegistrationInputValid(
                normalizedEmail,
                passwordHash,
                normalizedName
        )) {
            // 상위 계층 검사를 우회한 생성에 대한 도메인 불변식 방어
            throw new IllegalArgumentException("회원가입 계정 값이 올바르지 않습니다.");
        }

        return new Account(normalizedEmail, passwordHash, normalizedName);
    }

    public static boolean isNameValid(String name) {
        return isNormalizedNameValid(normalizeName(name));
    }

    public boolean isLoginAllowed() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isPasswordChangeAllowed() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.LOCKED;
    }

    public boolean isNameChangeAllowed() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.LOCKED;
    }

    // 마지막 관리자 보호 대상이 되는 역할과 상태
    public boolean isUsableSystemAdministrator() {
        return globalRole == GlobalRole.SYSTEM_ADMIN
                && (status == AccountStatus.ACTIVE || status == AccountStatus.LOCKED);
    }

    // 명세에서 허용한 본인 탈퇴 시작 상태
    public boolean isWithdrawalAllowed() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.LOCKED;
    }

    // 명세에서 허용한 관리자 비활성화 시작 상태
    public boolean isDisableAllowed() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.LOCKED;
    }

    // 명세에서 허용한 관리자 활성화 시작 상태
    public boolean isActivationAllowed() {
        return status == AccountStatus.ACTIVE
                || status == AccountStatus.LOCKED
                || status == AccountStatus.DISABLED;
    }

    public void changeName(String newName) {
        if (!isNameChangeAllowed()) {
            throw new IllegalStateException("현재 계정 상태에서는 이름을 변경할 수 없습니다.");
        }

        String normalizedName = normalizeName(newName);
        if (!isNormalizedNameValid(normalizedName)) {
            throw new IllegalArgumentException("이름은 앞뒤 공백을 제외하고 1~30자여야 합니다.");
        }

        name = normalizedName;
    }

    public void changePasswordHash(String newPasswordHash) {
        if (!isPasswordChangeAllowed()) {
            throw new IllegalStateException("현재 계정 상태에서는 비밀번호를 변경할 수 없습니다.");
        }
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("비밀번호 Hash는 비어 있을 수 없습니다.");
        }

        passwordHash = newPasswordHash;
    }

    public AccountStatusTransition withdraw(Instant withdrawnAt) {
        Instant occurredAt = Objects.requireNonNull(withdrawnAt, "withdrawnAt");
        AccountStatus before = status;

        // 도메인 객체의 중복 탈퇴 멱등 처리
        if (status == AccountStatus.WITHDRAWN) {
            return AccountStatusTransition.unchanged(status);
        }
        if (!isWithdrawalAllowed()) {
            throw new IllegalStateException("현재 계정 상태에서는 탈퇴할 수 없습니다.");
        }

        // 탈퇴 계정에 남길 필요가 없는 로그인 잠금 정보 정리
        status = AccountStatus.WITHDRAWN;
        failedLoginAttempts = 0;
        lockedUntil = null;
        this.withdrawnAt = occurredAt;
        return AccountStatusTransition.changed(before, status);
    }

    public AccountStatusTransition disable() {
        AccountStatus before = status;

        // 도메인 객체의 중복 비활성화 멱등 처리
        if (status == AccountStatus.DISABLED) {
            return AccountStatusTransition.unchanged(status);
        }
        if (!isDisableAllowed()) {
            throw new IllegalStateException("현재 계정 상태에서는 비활성화할 수 없습니다.");
        }

        // 비활성 계정에 남길 필요가 없는 로그인 잠금 정보 정리
        status = AccountStatus.DISABLED;
        failedLoginAttempts = 0;
        lockedUntil = null;
        return AccountStatusTransition.changed(before, status);
    }

    public AccountStatusTransition activate() {
        AccountStatus before = status;

        // 도메인 객체의 중복 활성화 멱등 처리
        if (status == AccountStatus.ACTIVE) {
            return AccountStatusTransition.unchanged(status);
        }
        if (!isActivationAllowed()) {
            throw new IllegalStateException("현재 계정 상태에서는 활성화할 수 없습니다.");
        }

        status = AccountStatus.ACTIVE;
        failedLoginAttempts = 0;
        lockedUntil = null;
        return AccountStatusTransition.changed(before, status);
    }

    public void recoverExpiredLoginLock(Instant now) {
        Instant checkedAt = Objects.requireNonNull(now, "now");

        if (status != AccountStatus.LOCKED) {
            return;
        }
        if (lockedUntil == null) {
            throw new IllegalStateException("잠긴 계정에는 잠금 종료 시각이 필요합니다.");
        }
        if (checkedAt.isBefore(lockedUntil)) {
            return;
        }

        status = AccountStatus.ACTIVE;
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void recordLoginFailure(
            Instant now,
            int maximumFailedAttempts,
            Duration lockDuration
    ) {
        Instant failedAt = Objects.requireNonNull(now, "now");
        Duration duration = requireLockDuration(lockDuration);

        if (maximumFailedAttempts < 1 || maximumFailedAttempts > Short.MAX_VALUE) {
            throw new IllegalArgumentException("최대 로그인 실패 횟수 범위가 올바르지 않습니다.");
        }
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("활성 계정에만 로그인 실패를 기록할 수 있습니다.");
        }

        int nextFailedAttempts = failedLoginAttempts + 1;
        if (nextFailedAttempts >= maximumFailedAttempts) {
            failedLoginAttempts = (short) maximumFailedAttempts;
            status = AccountStatus.LOCKED;
            lockedUntil = failedAt.plus(duration);
            return;
        }

        failedLoginAttempts = (short) nextFailedAttempts;
    }

    public void recordLoginSuccess() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("활성 계정에만 로그인 성공을 기록할 수 있습니다.");
        }

        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private static Duration requireLockDuration(Duration lockDuration) {
        Duration duration = Objects.requireNonNull(lockDuration, "lockDuration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("로그인 잠금 기간은 0보다 커야 합니다.");
        }
        return duration;
    }

    private static boolean isNormalizedRegistrationInputValid(
            String normalizedEmail,
            String passwordHash,
            String normalizedName
    ) {
        return EmailPolicy.isSatisfiedBy(normalizedEmail)
                && isNormalizedNameValid(normalizedName)
                && passwordHash != null
                && !passwordHash.isBlank();
    }

    private static boolean isNormalizedNameValid(String normalizedName) {
        return !normalizedName.isEmpty()
                && normalizedName.length() <= 30;
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
