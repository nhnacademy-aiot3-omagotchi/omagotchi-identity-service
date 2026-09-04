package site.omagotchi.identityservice.auth.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

class PasswordChangeServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000635"
    );
    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";

    @Test
    @DisplayName("계정 비밀번호 교체 후 모든 Refresh Session 폐기")
    void replacesPasswordBeforeRevokingSessions() {
        // Given
        AccountPasswordService accountPasswordService = mock(AccountPasswordService.class);
        RefreshSessionRevocationService revocationService = mock(
                RefreshSessionRevocationService.class
        );
        PasswordChangeService service = new PasswordChangeService(
                accountPasswordService,
                revocationService
        );

        // When
        service.changePassword(ACCOUNT_ID, CURRENT_PASSWORD, NEW_PASSWORD);

        // Then
        InOrder invocationOrder = inOrder(accountPasswordService, revocationService);
        invocationOrder.verify(accountPasswordService).verifyAndReplacePasswordHash(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );
        invocationOrder.verify(revocationService).revokeAllForAccount(
                ACCOUNT_ID,
                RefreshSessionRevocationReason.PASSWORD_CHANGED
        );
        verifyNoMoreInteractions(accountPasswordService, revocationService);
    }

    @Test
    @DisplayName("계정 비밀번호 교체 실패 시 Refresh Session 폐기 생략")
    void preservesSessionsWhenPasswordReplacementFails() {
        // Given
        AccountPasswordService accountPasswordService = mock(AccountPasswordService.class);
        RefreshSessionRevocationService revocationService = mock(
                RefreshSessionRevocationService.class
        );
        PasswordChangeService service = new PasswordChangeService(
                accountPasswordService,
                revocationService
        );
        BusinessException failure = new BusinessException(
                AccountErrorCode.CURRENT_PASSWORD_MISMATCH
        );
        willThrow(failure).given(accountPasswordService).verifyAndReplacePasswordHash(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );

        // When
        Throwable thrown = catchThrowable(() -> service.changePassword(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
        ));

        // Then
        then(thrown).isSameAs(failure);
        verifyNoInteractions(revocationService);
    }

    @Test
    @DisplayName("Session 폐기 실패 시 예외 전파 (트랜잭션 롤백 유도)")
    void propagatesExceptionWhenSessionRevocationFails() {
        // Given
        AccountPasswordService accountPasswordService = mock(AccountPasswordService.class);
        RefreshSessionRevocationService revocationService = mock(
                RefreshSessionRevocationService.class
        );
        PasswordChangeService service = new PasswordChangeService(
                accountPasswordService,
                revocationService
        );
        willThrow(new IllegalStateException("의도한 Refresh Session 폐기 실패"))
                .given(revocationService)
                .revokeAllForAccount(
                        ACCOUNT_ID,
                        RefreshSessionRevocationReason.PASSWORD_CHANGED
                );

        // When
        Throwable thrown = catchThrowable(() -> service.changePassword(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
        ));

        // Then
        then(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage("의도한 Refresh Session 폐기 실패");
        verify(accountPasswordService).verifyAndReplacePasswordHash(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );
    }
}
