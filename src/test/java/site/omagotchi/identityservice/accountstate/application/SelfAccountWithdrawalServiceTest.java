package site.omagotchi.identityservice.accountstate.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.account.application.AccountLifecycleService;
import site.omagotchi.identityservice.account.application.result.AccountStateChangeResult;
import site.omagotchi.identityservice.account.application.result.AccountStateValue;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationReason;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationService;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SelfAccountWithdrawalServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000301"
    );
    private static final String PASSWORD = "password-passphrase";

    @Mock
    private AccountLifecycleService accountLifecycleService;
    @Mock
    private RefreshSessionRevocationService refreshSessionRevocationService;

    @InjectMocks
    private SelfAccountWithdrawalService service;

    @Test
    @DisplayName("본인 탈퇴 성공 시 모든 Refresh Session 폐기")
    void withdrawsAccountAndRevokesSessions() {
        // Given
        AccountStateChangeResult result = new AccountStateChangeResult(
                ACCOUNT_ID,
                AccountStateValue.ACTIVE,
                AccountStateValue.WITHDRAWN
        );
        given(accountLifecycleService.withdraw(ACCOUNT_ID, PASSWORD)).willReturn(result);

        // When
        service.withdraw(ACCOUNT_ID, PASSWORD);

        // Then
        verify(accountLifecycleService).withdraw(ACCOUNT_ID, PASSWORD);
        verify(refreshSessionRevocationService).revokeAllForAccount(
                ACCOUNT_ID,
                RefreshSessionRevocationReason.ACCOUNT_WITHDRAWN
        );
    }

    @Test
    @DisplayName("이미 탈퇴된 계정의 재탈퇴 요청은 Refresh Session 재폐기 생략")
    void skipsRevocationWhenWithdrawalDidNotChange() {
        // Given
        AccountStateChangeResult result = new AccountStateChangeResult(
                ACCOUNT_ID,
                AccountStateValue.WITHDRAWN,
                AccountStateValue.WITHDRAWN
        );
        given(accountLifecycleService.withdraw(ACCOUNT_ID, PASSWORD)).willReturn(result);

        // When
        service.withdraw(ACCOUNT_ID, PASSWORD);

        // Then
        verify(accountLifecycleService).withdraw(ACCOUNT_ID, PASSWORD);
        verifyNoInteractions(refreshSessionRevocationService);
    }

    @Test
    @DisplayName("Refresh Session 폐기 실패 시 예외 전파 (트랜잭션 롤백 유도)")
    void propagatesExceptionWhenSessionRevocationFails() {
        // Given
        AccountStateChangeResult result = new AccountStateChangeResult(
                ACCOUNT_ID,
                AccountStateValue.ACTIVE,
                AccountStateValue.WITHDRAWN
        );
        given(accountLifecycleService.withdraw(ACCOUNT_ID, PASSWORD)).willReturn(result);
        willThrow(new IllegalStateException("의도한 Refresh Session 폐기 실패"))
                .given(refreshSessionRevocationService)
                .revokeAllForAccount(ACCOUNT_ID, RefreshSessionRevocationReason.ACCOUNT_WITHDRAWN);

        // When
        Throwable thrown = catchThrowable(() -> service.withdraw(ACCOUNT_ID, PASSWORD));

        // Then
        then(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage("의도한 Refresh Session 폐기 실패");
    }
}
