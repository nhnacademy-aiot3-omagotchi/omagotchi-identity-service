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

import java.time.Instant;
import java.util.Arrays;
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

        if (!isNormalizedRegistrationInputValid(
                normalizedEmail,
                passwordHash,
                normalizedName
        )) {
            // Application 검사를 우회한 호출에 대한 Domain 불변식 방어
            throw new IllegalArgumentException("회원가입 계정 값이 올바르지 않습니다.");
        }

        return new Account(normalizedEmail, passwordHash, normalizedName);
    }

    public static boolean isRegistrationEmailValid(String email) {
        return isNormalizedRegistrationEmailValid(normalizeEmail(email));
    }

    public static boolean isRegistrationNameValid(String name) {
        return isNormalizedRegistrationNameValid(normalize(name));
    }

    public boolean isLoginAllowed() {
        return status == AccountStatus.ACTIVE;
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

    private static boolean isNormalizedRegistrationInputValid(
            String normalizedEmail,
            String passwordHash,
            String normalizedName
    ) {
        return isNormalizedRegistrationDetailsValid(normalizedEmail, normalizedName)
                && passwordHash != null
                && !passwordHash.isBlank();
    }

    private static boolean isNormalizedRegistrationDetailsValid(
            String normalizedEmail,
            String normalizedName
    ) {
        return isNormalizedRegistrationEmailValid(normalizedEmail)
                && isNormalizedRegistrationNameValid(normalizedName);
    }

    private static boolean isNormalizedRegistrationEmailValid(String normalizedEmail) {
        return isEmailFormatValid(normalizedEmail)
                && normalizedEmail.length() <= 254;
    }

    private static boolean isNormalizedRegistrationNameValid(String normalizedName) {
        return !normalizedName.isEmpty()
                && normalizedName.length() <= 30;
    }

    // RFC 전체 검증이 아닌 서비스 허용 이메일의 최소 구조 검증
    private static boolean isEmailFormatValid(String email) {
        int separatorIndex = email.indexOf('@');
        if (separatorIndex <= 0
                || separatorIndex != email.lastIndexOf('@')
                || separatorIndex == email.length() - 1) {
            return false;
        }

        String localPart = email.substring(0, separatorIndex);
        String domainPart = email.substring(separatorIndex + 1);
        return isEmailLocalPartValid(localPart)
                && Arrays.stream(domainPart.split("\\.", -1)).allMatch(
                Account::isEmailDomainLabelValid
        );
    }

    private static boolean isEmailLocalPartValid(String localPart) {
        return localPart.length() <= 64
                && localPart.charAt(0) != '.'
                && localPart.charAt(localPart.length() - 1) != '.'
                && !localPart.contains("..")
                && localPart.chars().allMatch(Account::isEmailLocalCharacter);
    }

    private static boolean isEmailLocalCharacter(int character) {
        return Character.isLetterOrDigit(character)
                || "!#$%&'*+-/=?^_`{|}~.".indexOf(character) >= 0;
    }

    private static boolean isEmailDomainLabelValid(String label) {
        return !label.isEmpty()
                && label.length() <= 63
                && Character.isLetterOrDigit(label.charAt(0))
                && Character.isLetterOrDigit(label.charAt(label.length() - 1))
                && label.chars().allMatch(
                character -> Character.isLetterOrDigit(character) || character == '-'
        );
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
