package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.result.AccountRefreshAccess;
import site.omagotchi.identityservice.account.application.result.AccountSessionStateResult;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountSessionStateServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000216"
    );

    @Test
    @DisplayName("계정 행 잠금과 Session 상태 반환")
    void returnsAccountSessionStateAfterLock() {
        // Given
        AccountRepository accountRepository = mock(AccountRepository.class);
        Account account = mock(Account.class);
        given(accountRepository.lockById(ACCOUNT_ID)).willReturn(Optional.of(account));
        given(account.getId()).willReturn(ACCOUNT_ID);
        given(account.getGlobalRole()).willReturn(GlobalRole.SYSTEM_ADMIN);
        given(account.getStatus()).willReturn(AccountStatus.DISABLED);
        AccountSessionStateService service = new AccountSessionStateService(accountRepository);

        // When
        AccountSessionStateResult result = service.lockById(ACCOUNT_ID);

        // Then
        thenSoftly(softly -> {
            softly.then(result.accountId()).isEqualTo(ACCOUNT_ID);
            softly.then(result.globalRole()).isEqualTo(GlobalRole.SYSTEM_ADMIN.name());
            softly.then(result.refreshAccess())
                    .isEqualTo(AccountRefreshAccess.ACCOUNT_DISABLED);
        });
        verify(accountRepository).lockById(ACCOUNT_ID);
    }

    @Test
    @DisplayName("없는 계정의 Session 상태 조회 거부")
    void rejectsMissingAccount() {
        // Given
        AccountRepository accountRepository = mock(AccountRepository.class);
        given(accountRepository.lockById(ACCOUNT_ID)).willReturn(Optional.empty());
        AccountSessionStateService service = new AccountSessionStateService(accountRepository);

        // When
        Throwable thrown = catchThrowable(() -> service.lockById(ACCOUNT_ID));

        // Then
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.NOT_FOUND)
        );
    }
}
