package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AccountProfileServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountProfileService accountProfileService =
            new AccountProfileService(accountRepository);

    @Test
    @DisplayName("계정 행 잠금 뒤 이름 변경")
    void changesNameAfterLockingAccount() {
        // Given
        UUID accountId = UUID.randomUUID();
        Account account = Account.register(
                "user@example.com",
                "encoded-password",
                "기존 이름"
        );
        given(accountRepository.lockById(accountId)).willReturn(Optional.of(account));

        // When
        accountProfileService.changeName(accountId, "  새 이름  ");

        // Then
        then(account.getName()).isEqualTo("새 이름");
        verify(accountRepository).lockById(accountId);
    }

    @Test
    @DisplayName("잘못된 이름은 계정 조회 전에 거부")
    void rejectsInvalidNameBeforeLookup() {
        // When
        Throwable thrown = catchThrowable(() -> accountProfileService.changeName(
                UUID.randomUUID(),
                "가".repeat(31)
        ));

        // Then
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.INVALID_NAME)
        );
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("존재하지 않는 계정의 이름 변경 거부")
    void rejectsMissingAccount() {
        // Given
        UUID accountId = UUID.randomUUID();
        given(accountRepository.lockById(accountId)).willReturn(Optional.empty());

        // When
        Throwable thrown = catchThrowable(() -> accountProfileService.changeName(
                accountId,
                "새 이름"
        ));

        // Then
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.NOT_FOUND)
        );
    }

    @Test
    @DisplayName("이름 변경이 허용되지 않은 계정 상태 거부")
    void rejectsUnavailableAccount() {
        // Given
        UUID accountId = UUID.randomUUID();
        Account account = mock(Account.class);
        given(accountRepository.lockById(accountId)).willReturn(Optional.of(account));
        given(account.isNameChangeAllowed()).willReturn(false);

        // When
        Throwable thrown = catchThrowable(() -> accountProfileService.changeName(
                accountId,
                "새 이름"
        ));

        // Then
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.NAME_CHANGE_NOT_ALLOWED)
        );
        verify(account).isNameChangeAllowed();
    }
}
