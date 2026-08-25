package site.omagotchi.identityservice.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class AccountTest {

    @Test
    @DisplayName("가입 정보 정규화·기본 권한 및 상태")
    void registersAccount() {
        // Given
        String email = "  USER@Example.COM  ";
        String name = "  홍길동  ";

        // When
        Account account = Account.register(
                email,
                "encoded-password",
                name
        );

        // Then
        thenSoftly(softly -> {
            softly.then(account.getEmail()).isEqualTo("user@example.com");
            softly.then(account.getName()).isEqualTo("홍길동");
            softly.then(account.getGlobalRole()).isEqualTo(GlobalRole.USER);
            softly.then(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        });
    }

    @Test
    @DisplayName("가입 이름의 필수값·최대 길이 검증")
    void validatesRegistrationName() {
        // Given
        String maximumLengthName = "가".repeat(30);
        String tooLongName = maximumLengthName + "가";

        // Then
        thenSoftly(softly -> {
            softly.then(Account.isRegistrationNameValid("사용자")).isTrue();
            softly.then(Account.isRegistrationNameValid(maximumLengthName)).isTrue();
            softly.then(Account.isRegistrationNameValid(null)).isFalse();
            softly.then(Account.isRegistrationNameValid(" ")).isFalse();
            softly.then(Account.isRegistrationNameValid(tooLongName)).isFalse();
        });
    }

    @Test
    @DisplayName("올바르지 않은 이메일의 최종 생성 거부")
    void rejectsInvalidEmail() {
        // Given
        String passwordHash = "encoded-password";
        String name = "사용자";

        // When
        Throwable thrown = catchThrowable(() -> Account.register(
                "not-an-email",
                passwordHash,
                name
        ));

        // Then
        then(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호 길이·문자 허용 정책")
    void validatesPasswordLengthAndCharacters() {
        // Given
        String validPassword = "가나다라마바사 아자차카타파하";
        String tooShortPassword = "가".repeat(14);
        String blankPassword = " ".repeat(15);
        String passwordWithControlCharacter = "가".repeat(14) + "\n";

        // Then
        thenSoftly(softly -> {
            softly.then(PasswordPolicy.isSatisfiedBy(validPassword)).isTrue();
            softly.then(PasswordPolicy.isSatisfiedBy(null)).isFalse();
            softly.then(PasswordPolicy.isSatisfiedBy(tooShortPassword)).isFalse();
            softly.then(PasswordPolicy.isSatisfiedBy(blankPassword)).isFalse();
            softly.then(PasswordPolicy.isSatisfiedBy(passwordWithControlCharacter)).isFalse();
        });
    }

    @Test
    @DisplayName("UTF-8 72바이트 초과 입력 거부")
    void rejectsPasswordOverMaximumUtf8Bytes() {
        // Given
        String password = "가".repeat(24) + "a1";

        // Then
        then(PasswordPolicy.isSatisfiedBy(password)).isFalse();
    }

    @Test
    @DisplayName("설정된 연속 실패 횟수에 도달하면 계정 잠금")
    void locksAccountAtMaximumFailedAttempts() {
        // Given
        Account account = Account.register(
                "user@example.com",
                "encoded-password",
                "사용자"
        );
        Instant failedAt = Instant.parse("2026-08-24T00:00:00Z");
        Duration lockDuration = Duration.ofMinutes(10);

        // When
        for (int attempt = 1; attempt <= 5; attempt++) {
            account.recordLoginFailure(failedAt, 5, lockDuration);
        }

        // Then
        thenSoftly(softly -> {
            softly.then(account.getFailedLoginAttempts()).isEqualTo((short) 5);
            softly.then(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
            softly.then(account.getLockedUntil()).isEqualTo(failedAt.plus(lockDuration));
            softly.then(account.isLoginAllowed()).isFalse();
        });
    }

    @Test
    @DisplayName("성공한 로그인은 연속 실패 횟수 초기화")
    void resetsFailedAttemptsOnLoginSuccess() {
        // Given
        Account account = Account.register(
                "user@example.com",
                "encoded-password",
                "사용자"
        );
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        account.recordLoginFailure(now, 5, Duration.ofMinutes(10));
        account.recordLoginFailure(now, 5, Duration.ofMinutes(10));

        // When
        account.recordLoginSuccess();

        // Then
        thenSoftly(softly -> {
            softly.then(account.getFailedLoginAttempts()).isZero();
            softly.then(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(account.getLockedUntil()).isNull();
        });
    }

    @Test
    @DisplayName("잠금 종료 시각부터 활성 상태와 실패 횟수 복구")
    void recoversExpiredLoginLock() {
        // Given
        Account account = Account.register(
                "user@example.com",
                "encoded-password",
                "사용자"
        );
        Instant failedAt = Instant.parse("2026-08-24T00:00:00Z");
        Duration lockDuration = Duration.ofMinutes(10);
        for (int attempt = 1; attempt <= 5; attempt++) {
            account.recordLoginFailure(failedAt, 5, lockDuration);
        }

        // When
        account.recoverExpiredLoginLock(failedAt.plus(lockDuration));

        // Then
        thenSoftly(softly -> {
            softly.then(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(account.getFailedLoginAttempts()).isZero();
            softly.then(account.getLockedUntil()).isNull();
        });
    }

    @Test
    @DisplayName("잠금 종료 전에는 잠금 상태 유지")
    void keepsUnexpiredLoginLock() {
        // Given
        Account account = Account.register(
                "user@example.com",
                "encoded-password",
                "사용자"
        );
        Instant failedAt = Instant.parse("2026-08-24T00:00:00Z");
        Duration lockDuration = Duration.ofMinutes(10);
        for (int attempt = 1; attempt <= 5; attempt++) {
            account.recordLoginFailure(failedAt, 5, lockDuration);
        }

        // When
        account.recoverExpiredLoginLock(failedAt.plus(lockDuration).minusNanos(1));

        // Then
        thenSoftly(softly -> {
            softly.then(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
            softly.then(account.getFailedLoginAttempts()).isEqualTo((short) 5);
            softly.then(account.getLockedUntil()).isEqualTo(failedAt.plus(lockDuration));
        });
    }
}
