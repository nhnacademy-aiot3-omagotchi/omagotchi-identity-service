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
    @DisplayName("계정 이름의 필수값·최대 길이 검증")
    void validatesName() {
        // Given
        String maximumLengthName = "가".repeat(30);
        String tooLongName = maximumLengthName + "가";

        // Then
        thenSoftly(softly -> {
            softly.then(Account.isNameValid("사용자")).isTrue();
            softly.then(Account.isNameValid(maximumLengthName)).isTrue();
            softly.then(Account.isNameValid(null)).isFalse();
            softly.then(Account.isNameValid(" ")).isFalse();
            softly.then(Account.isNameValid(tooLongName)).isFalse();
        });
    }

    @Test
    @DisplayName("계정 이름 변경 시 앞뒤 공백 제거")
    void changesAndNormalizesName() {
        // Given
        Account account = Account.register(
                "user@example.com",
                "encoded-password",
                "기존 이름"
        );

        // When
        account.changeName("  새 이름  ");

        // Then
        then(account.getName()).isEqualTo("새 이름");
    }

    @Test
    @DisplayName("잘못된 이름 변경은 기존 이름 유지")
    void rejectsInvalidNameChange() {
        // Given
        Account account = Account.register(
                "user@example.com",
                "encoded-password",
                "기존 이름"
        );

        // When
        Throwable thrown = catchThrowable(() -> account.changeName(" "));

        // Then
        then(thrown).isInstanceOf(IllegalArgumentException.class);
        then(account.getName()).isEqualTo("기존 이름");
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
    @DisplayName("허용된 계정의 비밀번호 Hash 변경")
    void changesPasswordHashForAllowedAccount() {
        // Given
        Account account = Account.register(
                "user@example.com",
                "old-password-hash",
                "사용자"
        );

        // When
        account.changePasswordHash("new-password-hash");

        // Then
        then(account.getPasswordHash()).isEqualTo("new-password-hash");
    }

    @Test
    @DisplayName("빈 비밀번호 Hash 변경 거부")
    void rejectsBlankPasswordHash() {
        // Given
        Account account = Account.register(
                "user@example.com",
                "old-password-hash",
                "사용자"
        );

        // When
        Throwable thrown = catchThrowable(() -> account.changePasswordHash(" "));

        // Then
        then(thrown).isInstanceOf(IllegalArgumentException.class);
        then(account.getPasswordHash()).isEqualTo("old-password-hash");
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

    @Test
    @DisplayName("활성 계정 탈퇴 시 WITHDRAWN 전환과 탈퇴 시각 기록")
    void withdrawsActiveAccount() {
        // Given
        Account account = account();
        Instant withdrawnAt = Instant.parse("2026-08-30T12:00:00Z");

        // When
        AccountStatusTransition transition = account.withdraw(withdrawnAt);

        // Then
        thenSoftly(softly -> {
            softly.then(transition.before()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(transition.after()).isEqualTo(AccountStatus.WITHDRAWN);
            softly.then(transition.changed()).isTrue();
            softly.then(account.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
            softly.then(account.getWithdrawnAt()).isEqualTo(withdrawnAt);
            softly.then(account.getFailedLoginAttempts()).isZero();
            softly.then(account.getLockedUntil()).isNull();
        });
    }

    @Test
    @DisplayName("잠긴 계정 탈퇴 시 로그인 잠금 정보 정리")
    void withdrawsLockedAccountAndClearsLoginLock() {
        // Given
        Account account = lockedAccount();
        Instant withdrawnAt = Instant.parse("2026-08-30T12:00:00Z");

        // When
        account.withdraw(withdrawnAt);

        // Then
        thenSoftly(softly -> {
            softly.then(account.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
            softly.then(account.getWithdrawnAt()).isEqualTo(withdrawnAt);
            softly.then(account.getFailedLoginAttempts()).isZero();
            softly.then(account.getLockedUntil()).isNull();
        });
    }

    @Test
    @DisplayName("탈퇴 재요청은 최초 탈퇴 시각을 보존하는 No-op")
    void preservesFirstWithdrawalOnRepeatedRequest() {
        // Given
        Account account = account();
        Instant firstWithdrawal = Instant.parse("2026-08-30T12:00:00Z");
        account.withdraw(firstWithdrawal);

        // When
        AccountStatusTransition transition = account.withdraw(
                firstWithdrawal.plusSeconds(60)
        );

        // Then
        thenSoftly(softly -> {
            softly.then(transition.changed()).isFalse();
            softly.then(account.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
            softly.then(account.getWithdrawnAt()).isEqualTo(firstWithdrawal);
        });
    }

    @Test
    @DisplayName("비활성 계정의 본인 탈퇴 거부")
    void rejectsWithdrawalOfDisabledAccount() {
        // Given
        Account account = account();
        account.disable();

        // When
        Throwable thrown = catchThrowable(() -> account.withdraw(
                Instant.parse("2026-08-30T12:00:00Z")
        ));

        // Then
        then(thrown).isInstanceOf(IllegalStateException.class);
        then(account.getStatus()).isEqualTo(AccountStatus.DISABLED);
    }

    @Test
    @DisplayName("활성·잠금 계정 비활성화와 잠금 정보 정리")
    void disablesActiveAndLockedAccounts() {
        // Given
        Account active = account();
        Account locked = lockedAccount();

        then(active.isDisableAllowed()).isTrue();
        then(locked.isDisableAllowed()).isTrue();

        // When
        AccountStatusTransition activeTransition = active.disable();
        AccountStatusTransition lockedTransition = locked.disable();

        // Then
        thenSoftly(softly -> {
            softly.then(activeTransition.before()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(activeTransition.after()).isEqualTo(AccountStatus.DISABLED);
            softly.then(lockedTransition.before()).isEqualTo(AccountStatus.LOCKED);
            softly.then(lockedTransition.after()).isEqualTo(AccountStatus.DISABLED);
            softly.then(locked.getFailedLoginAttempts()).isZero();
            softly.then(locked.getLockedUntil()).isNull();
            softly.then(active.isDisableAllowed()).isFalse();
            softly.then(locked.isDisableAllowed()).isFalse();
        });
    }

    @Test
    @DisplayName("잠금 해제·재활성화와 동일 상태 No-op")
    void activatesLockedAndDisabledAccountsIdempotently() {
        // Given
        Account locked = lockedAccount();
        Account disabled = account();
        disabled.disable();

        then(locked.isActivationAllowed()).isTrue();
        then(disabled.isActivationAllowed()).isTrue();

        // When
        AccountStatusTransition unlocked = locked.activate();
        AccountStatusTransition reactivated = disabled.activate();
        AccountStatusTransition unchanged = disabled.activate();

        // Then
        thenSoftly(softly -> {
            softly.then(unlocked.before()).isEqualTo(AccountStatus.LOCKED);
            softly.then(unlocked.after()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(reactivated.before()).isEqualTo(AccountStatus.DISABLED);
            softly.then(reactivated.after()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(unchanged.changed()).isFalse();
            softly.then(locked.getFailedLoginAttempts()).isZero();
            softly.then(locked.getLockedUntil()).isNull();
        });
    }

    @Test
    @DisplayName("탈퇴 계정의 관리자 상태 변경 거부")
    void rejectsAdministrativeTransitionFromWithdrawnAccount() {
        // Given
        Account account = account();
        account.withdraw(Instant.parse("2026-08-30T12:00:00Z"));

        // When
        boolean disableAllowed = account.isDisableAllowed();
        boolean activationAllowed = account.isActivationAllowed();
        Throwable disableFailure = catchThrowable(account::disable);
        Throwable activationFailure = catchThrowable(account::activate);

        // Then
        then(disableAllowed).isFalse();
        then(activationAllowed).isFalse();
        then(disableFailure).isInstanceOf(IllegalStateException.class);
        then(activationFailure).isInstanceOf(IllegalStateException.class);
        then(account.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
    }

    private Account account() {
        return Account.register(
                "state-user@example.com",
                "encoded-password",
                "사용자"
        );
    }

    private Account lockedAccount() {
        Account account = account();
        Instant failedAt = Instant.parse("2026-08-30T00:00:00Z");
        for (int attempt = 0; attempt < 5; attempt++) {
            account.recordLoginFailure(failedAt, 5, Duration.ofMinutes(10));
        }
        return account;
    }
}
