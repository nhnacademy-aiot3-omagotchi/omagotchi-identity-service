package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class AccountPasswordServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000635"
    );
    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";
    private static final String CURRENT_PASSWORD_HASH = "current-password-hash";
    private static final String NEW_PASSWORD_HASH = "new-password-hash";

    @Test
    @DisplayName("OTP 발급용 이메일은 계정 행 잠금 없이 조회")
    void getsPasswordChangeEmailWithoutLocking() {
        // Given
        Account account = account();
        Fixture fixture = fixture(Optional.of(account));
        given(fixture.accountRepository().findById(ACCOUNT_ID)).willReturn(Optional.of(account));

        // When
        String email = fixture.service().getPasswordChangeEmail(ACCOUNT_ID);

        // Then
        then(email).isEqualTo("user@example.com");
        verify(fixture.accountRepository()).findById(ACCOUNT_ID);
        verify(fixture.accountRepository(), never()).lockById(ACCOUNT_ID);
        verifyNoInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("비밀번호 변경 Transaction용 이메일은 계정 행 잠금 후 조회")
    void locksAndGetsPasswordChangeEmail() {
        // Given
        Account account = account();
        Fixture fixture = fixture(Optional.of(account));

        // When
        String email = fixture.service().lockPasswordChangeEmail(ACCOUNT_ID);

        // Then
        then(email).isEqualTo("user@example.com");
        verify(fixture.accountRepository()).lockById(ACCOUNT_ID);
        verify(fixture.accountRepository(), never()).findById(ACCOUNT_ID);
        verifyNoInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("현재 비밀번호 확인 후 Hash 교체")
    void verifiesCurrentPasswordAndReplacesHash() {
        // Given
        Account account = account();
        Fixture fixture = fixture(Optional.of(account));
        given(fixture.passwordHasher().matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).willReturn(true);
        given(fixture.passwordHasher().matches(
                NEW_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).willReturn(false);
        given(fixture.passwordHasher().hash(NEW_PASSWORD)).willReturn(NEW_PASSWORD_HASH);

        // When
        fixture.service().verifyAndReplacePasswordHash(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );

        // Then
        then(account.getPasswordHash()).isEqualTo(NEW_PASSWORD_HASH);
        InOrder invocationOrder = inOrder(
                fixture.accountRepository(),
                fixture.passwordHasher()
        );
        invocationOrder.verify(fixture.accountRepository()).lockById(ACCOUNT_ID);
        invocationOrder.verify(fixture.passwordHasher()).matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        );
        invocationOrder.verify(fixture.passwordHasher()).matches(
                NEW_PASSWORD,
                CURRENT_PASSWORD_HASH
        );
        invocationOrder.verify(fixture.passwordHasher()).hash(NEW_PASSWORD);
        verifyNoMoreInteractions(
                fixture.accountRepository(),
                fixture.passwordHasher()
        );
    }

    @Test
    @DisplayName("존재하지 않는 계정의 비밀번호 Hash 교체 거부")
    void rejectsMissingAccount() {
        // Given
        Fixture fixture = fixture(Optional.empty());

        // When
        Throwable thrown = catchThrowable(() -> fixture.service()
                .verifyAndReplacePasswordHash(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                ));

        // Then
        thenBusinessError(thrown, AccountErrorCode.NOT_FOUND);
        verify(fixture.accountRepository()).lockById(ACCOUNT_ID);
        verifyNoInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("현재 비밀번호 불일치 시 Hash 유지")
    void rejectsMismatchedCurrentPassword() {
        // Given
        Account account = account();
        Fixture fixture = fixture(Optional.of(account));
        given(fixture.passwordHasher().matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).willReturn(false);

        // When
        Throwable thrown = catchThrowable(() -> fixture.service()
                .verifyAndReplacePasswordHash(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                ));

        // Then
        thenBusinessError(thrown, AccountErrorCode.CURRENT_PASSWORD_MISMATCH);
        then(account.getPasswordHash()).isEqualTo(CURRENT_PASSWORD_HASH);
        verify(fixture.passwordHasher()).matches(CURRENT_PASSWORD, CURRENT_PASSWORD_HASH);
        verifyNoMoreInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("누락된 현재 비밀번호를 불일치로 처리")
    void rejectsMissingCurrentPassword() {
        // Given
        Account account = account();
        Fixture fixture = fixture(Optional.of(account));
        given(fixture.passwordHasher().matches("", CURRENT_PASSWORD_HASH)).willReturn(false);

        // When
        Throwable thrown = catchThrowable(() -> fixture.service()
                .verifyAndReplacePasswordHash(ACCOUNT_ID, null, NEW_PASSWORD));

        // Then
        thenBusinessError(thrown, AccountErrorCode.CURRENT_PASSWORD_MISMATCH);
        then(account.getPasswordHash()).isEqualTo(CURRENT_PASSWORD_HASH);
        verify(fixture.passwordHasher()).matches("", CURRENT_PASSWORD_HASH);
        verifyNoMoreInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("정책을 위반한 새 비밀번호 거부")
    void rejectsInvalidNewPassword() {
        // Given
        Account account = account();
        Fixture fixture = fixture(Optional.of(account));
        given(fixture.passwordHasher().matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).willReturn(true);

        // When
        Throwable thrown = catchThrowable(() -> fixture.service()
                .verifyAndReplacePasswordHash(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        "too-short"
                ));

        // Then
        thenBusinessError(thrown, AccountErrorCode.INVALID_PASSWORD);
        then(account.getPasswordHash()).isEqualTo(CURRENT_PASSWORD_HASH);
        verify(fixture.passwordHasher()).matches(CURRENT_PASSWORD, CURRENT_PASSWORD_HASH);
        verifyNoMoreInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("현재와 같은 새 비밀번호 거부")
    void rejectsUnchangedPassword() {
        // Given
        Account account = account();
        Fixture fixture = fixture(Optional.of(account));
        given(fixture.passwordHasher().matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        )).willReturn(true);

        // When
        Throwable thrown = catchThrowable(() -> fixture.service()
                .verifyAndReplacePasswordHash(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        CURRENT_PASSWORD
                ));

        // Then
        thenBusinessError(thrown, AccountErrorCode.PASSWORD_UNCHANGED);
        then(account.getPasswordHash()).isEqualTo(CURRENT_PASSWORD_HASH);
        verify(fixture.passwordHasher(), times(2)).matches(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD_HASH
        );
        verifyNoMoreInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("비밀번호 변경이 허용되지 않은 계정 상태 거부")
    void rejectsUnavailableAccount() {
        // Given
        Account account = mock(Account.class);
        Fixture fixture = fixture(Optional.of(account));
        given(account.isPasswordChangeAllowed()).willReturn(false);

        // When
        Throwable thrown = catchThrowable(() -> fixture.service()
                .verifyAndReplacePasswordHash(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                ));

        // Then
        thenBusinessError(thrown, AccountErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
        verifyNoInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("비밀번호 재설정 이메일 검증과 정규화")
    void validatesAndNormalizesPasswordResetEmail() {
        // Given
        Fixture fixture = fixture(Optional.empty());

        // When
        String normalized = fixture.service()
                .validateAndNormalizePasswordResetEmail("  USER@Example.COM  ");
        Throwable invalid = catchThrowable(() -> fixture.service()
                .validateAndNormalizePasswordResetEmail("not-an-email"));

        // Then
        then(normalized).isEqualTo("user@example.com");
        thenBusinessError(invalid, AccountErrorCode.INVALID_EMAIL);
        verifyNoInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("재설정 가능한 계정을 이메일로 잠그고 식별자만 반환")
    void locksPasswordResetAccountByEmail() {
        // Given
        Account account = mock(Account.class);
        Fixture fixture = fixture(Optional.empty());
        given(account.getId()).willReturn(ACCOUNT_ID);
        given(account.isPasswordChangeAllowed()).willReturn(true);
        given(fixture.accountRepository().lockByEmail("user@example.com"))
                .willReturn(Optional.of(account));

        // When
        Optional<UUID> found = fixture.service()
                .lockPasswordResetAccountId("user@example.com");

        // Then
        then(found).contains(ACCOUNT_ID);
        verify(fixture.accountRepository()).lockByEmail("user@example.com");
        verifyNoInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("재설정 불가 계정은 존재 여부를 구분하지 않는 빈 결과")
    void hidesUnavailablePasswordResetAccount() {
        // Given
        Account account = mock(Account.class);
        Fixture fixture = fixture(Optional.empty());
        given(account.isPasswordChangeAllowed()).willReturn(false);
        given(fixture.accountRepository().lockByEmail("user@example.com"))
                .willReturn(Optional.of(account));

        // When
        Optional<UUID> found = fixture.service()
                .lockPasswordResetAccountId("user@example.com");

        // Then
        then(found).isEmpty();
        verify(fixture.accountRepository()).lockByEmail("user@example.com");
        verifyNoInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("재설정 비밀번호 Hash 교체와 로그인 잠금 초기화 위임")
    void replacesPasswordHashForReset() {
        // Given
        Account account = mock(Account.class);
        Fixture fixture = fixture(Optional.of(account));
        given(account.isPasswordChangeAllowed()).willReturn(true);
        given(account.getPasswordHash()).willReturn(CURRENT_PASSWORD_HASH);
        given(fixture.passwordHasher().matches(NEW_PASSWORD, CURRENT_PASSWORD_HASH))
                .willReturn(false);
        given(fixture.passwordHasher().hash(NEW_PASSWORD)).willReturn(NEW_PASSWORD_HASH);

        // When
        boolean replaced = fixture.service().replacePasswordHashForReset(
                ACCOUNT_ID,
                NEW_PASSWORD
        );

        // Then
        then(replaced).isTrue();
        verify(fixture.accountRepository()).lockById(ACCOUNT_ID);
        verify(account).resetPasswordHash(NEW_PASSWORD_HASH);
    }

    @Test
    @DisplayName("기존 비밀번호와 같은 재설정 요청은 이유를 노출하지 않고 거절")
    void rejectsUnchangedPasswordResetWithoutExposingReason() {
        // Given
        Account account = mock(Account.class);
        Fixture fixture = fixture(Optional.of(account));
        given(account.isPasswordChangeAllowed()).willReturn(true);
        given(account.getPasswordHash()).willReturn(CURRENT_PASSWORD_HASH);
        given(fixture.passwordHasher().matches(NEW_PASSWORD, CURRENT_PASSWORD_HASH))
                .willReturn(true);

        // When
        boolean replaced = fixture.service().replacePasswordHashForReset(
                ACCOUNT_ID,
                NEW_PASSWORD
        );

        // Then
        then(replaced).isFalse();
        verify(account, never()).resetPasswordHash(NEW_PASSWORD_HASH);
        verify(fixture.passwordHasher(), never()).hash(NEW_PASSWORD);
    }

    private Fixture fixture(Optional<Account> account) {
        AccountRepository accountRepository = mock(AccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        given(accountRepository.lockById(ACCOUNT_ID)).willReturn(account);
        return new Fixture(
                accountRepository,
                passwordHasher,
                new AccountPasswordService(accountRepository, passwordHasher)
        );
    }

    private Account account() {
        return Account.register(
                "user@example.com",
                CURRENT_PASSWORD_HASH,
                "사용자",
                Instant.EPOCH
        );
    }

    private void thenBusinessError(Throwable thrown, AccountErrorCode errorCode) {
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode()).isEqualTo(errorCode)
        );
    }

    private record Fixture(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            AccountPasswordService service
    ) {
    }
}
