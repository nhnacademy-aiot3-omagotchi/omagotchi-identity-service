package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AccountRegistrationServiceTest {

    @Test
    @DisplayName("비밀번호 최대 UTF-8 바이트 위반을 가입 입력 오류로 변환")
    void rejectsPasswordOverMaximumUtf8Bytes() {
        // Given
        AccountRepository accountRepository = mock(AccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        AccountRegistrationService accountRegistrationService = new AccountRegistrationService(
                accountRepository,
                passwordHasher
        );
        String password = "가".repeat(24) + "a1";

        // When
        Throwable thrown = catchThrowable(() -> accountRegistrationService.signUp(
                "user@example.com",
                password,
                "사용자"
        ));

        // Then
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.INVALID_SIGNUP_INPUT)
        );
        verifyNoInteractions(accountRepository, passwordHasher);
    }

    @Test
    @DisplayName("HTTP 외부에서도 잘못된 이메일을 가입 입력 오류로 변환")
    void rejectsInvalidEmailOutsidePresentation() {
        // Given
        AccountRepository accountRepository = mock(AccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        AccountRegistrationService accountRegistrationService = new AccountRegistrationService(
                accountRepository,
                passwordHasher
        );

        // When
        Throwable thrown = catchThrowable(() -> accountRegistrationService.signUp(
                "not-an-email",
                "password-passphrase",
                "사용자"
        ));

        // Then
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.INVALID_SIGNUP_INPUT)
        );
        verifyNoInteractions(accountRepository, passwordHasher);
    }

    @Test
    @DisplayName("저장소가 분류한 업무 실패는 다시 감싸지 않음")
    void preservesRepositoryBusinessFailure() {
        // Given
        IllegalStateException persistenceFailure =
                new IllegalStateException("persistence failure");
        BusinessException repositoryFailure =
                new BusinessException(AccountErrorCode.DUPLICATE_EMAIL, persistenceFailure);

        AccountRepository accountRepository = mock(AccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        given(passwordHasher.hash("password-passphrase"))
                .willReturn("encoded-password");
        given(accountRepository.create(any(Account.class)))
                .willThrow(repositoryFailure);

        AccountRegistrationService accountRegistrationService = new AccountRegistrationService(
                accountRepository,
                passwordHasher
        );

        // When
        Throwable thrown = catchThrowable(() -> accountRegistrationService.signUp(
                "user@example.com",
                "password-passphrase",
                "사용자"
        ));

        // Then
        then(thrown)
                .isSameAs(repositoryFailure)
                .hasCause(persistenceFailure);
    }

    @Test
    @DisplayName("예상하지 못한 저장 실패는 원본 그대로 전파")
    void preservesUnexpectedRepositoryFailure() {
        // Given
        RuntimeException repositoryFailure = new RuntimeException("database unavailable");
        AccountRepository accountRepository = mock(AccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        given(passwordHasher.hash("password-passphrase"))
                .willReturn("encoded-password");
        given(accountRepository.create(any(Account.class)))
                .willThrow(repositoryFailure);

        AccountRegistrationService accountRegistrationService = new AccountRegistrationService(
                accountRepository,
                passwordHasher
        );

        // When
        Throwable thrown = catchThrowable(() -> accountRegistrationService.signUp(
                "user@example.com",
                "password-passphrase",
                "사용자"
        ));

        // Then
        then(thrown).isSameAs(repositoryFailure);
    }
}
