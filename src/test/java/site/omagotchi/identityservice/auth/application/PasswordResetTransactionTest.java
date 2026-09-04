package site.omagotchi.identityservice.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationUseService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetTransactionTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000702201"
    );
    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000702202"
    );
    private static final String EMAIL = "member@example.com";
    private static final String NEW_PASSWORD = "new-password-passphrase";

    @Mock
    private AccountPasswordService accountPasswordService;
    @Mock
    private RefreshSessionRevocationService revocationService;
    @Mock
    private EmailVerificationUseService emailVerificationUseService;

    private PasswordResetTransaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new PasswordResetTransaction(
                accountPasswordService,
                revocationService,
                emailVerificationUseService
        );
    }

    @Test
    @DisplayName("계정·Challenge·Refresh Session 순서로 재설정")
    void resetsPasswordInLockOrder() {
        // Given
        given(accountPasswordService.lockPasswordResetAccountId(EMAIL))
                .willReturn(Optional.of(ACCOUNT_ID));
        given(emailVerificationUseService.verifyPasswordResetOtp(
                CHALLENGE_ID,
                EMAIL,
                "123456"
        ))
                .willReturn(true);
        given(accountPasswordService.replacePasswordHashForReset(ACCOUNT_ID, NEW_PASSWORD))
                .willReturn(true);

        // When
        boolean reset = transaction.resetPassword(
                EMAIL,
                NEW_PASSWORD,
                CHALLENGE_ID,
                "123456"
        );

        // Then
        then(reset).isTrue();
        InOrder order = inOrder(
                accountPasswordService,
                emailVerificationUseService,
                revocationService
        );
        order.verify(accountPasswordService).lockPasswordResetAccountId(EMAIL);
        order.verify(emailVerificationUseService).verifyPasswordResetOtp(
                CHALLENGE_ID,
                EMAIL,
                "123456"
        );
        order.verify(accountPasswordService).replacePasswordHashForReset(
                ACCOUNT_ID,
                NEW_PASSWORD
        );
        order.verify(revocationService).revokeAllForAccount(
                ACCOUNT_ID,
                RefreshSessionRevocationReason.PASSWORD_RESET
        );
        order.verify(emailVerificationUseService).consume(CHALLENGE_ID);
    }

    @Test
    @DisplayName("잘못된 OTP는 비밀번호와 Session을 변경하지 않음")
    void keepsAccountWhenOtpFails() {
        // Given
        given(accountPasswordService.lockPasswordResetAccountId(EMAIL))
                .willReturn(Optional.of(ACCOUNT_ID));
        given(emailVerificationUseService.verifyPasswordResetOtp(
                CHALLENGE_ID,
                EMAIL,
                "000000"
        ))
                .willReturn(false);

        // When
        boolean reset = transaction.resetPassword(
                EMAIL,
                NEW_PASSWORD,
                CHALLENGE_ID,
                "000000"
        );

        // Then
        then(reset).isFalse();
        verify(accountPasswordService, never()).replacePasswordHashForReset(
                ACCOUNT_ID,
                NEW_PASSWORD
        );
        verify(revocationService, never()).revokeAllForAccount(
                ACCOUNT_ID,
                RefreshSessionRevocationReason.PASSWORD_RESET
        );
        verify(emailVerificationUseService, never()).consume(CHALLENGE_ID);
    }

    @Test
    @DisplayName("없는 계정의 올바른 OTP는 소비 후 일반 실패")
    void consumesOtpForMissingAccount() {
        // Given
        given(accountPasswordService.lockPasswordResetAccountId(EMAIL))
                .willReturn(Optional.empty());
        given(emailVerificationUseService.verifyPasswordResetOtp(
                CHALLENGE_ID,
                EMAIL,
                "123456"
        ))
                .willReturn(true);

        // When
        boolean reset = transaction.resetPassword(
                EMAIL,
                NEW_PASSWORD,
                CHALLENGE_ID,
                "123456"
        );

        // Then
        then(reset).isFalse();
        verify(emailVerificationUseService).consume(CHALLENGE_ID);
        verify(revocationService, never()).revokeAllForAccount(
                ACCOUNT_ID,
                RefreshSessionRevocationReason.PASSWORD_RESET
        );
    }

    @Test
    @DisplayName("계정의 비밀번호 교체 거절은 Session과 Challenge를 변경하지 않음")
    void keepsSessionAndChallengeWhenAccountRejectsReplacement() {
        // Given
        given(accountPasswordService.lockPasswordResetAccountId(EMAIL))
                .willReturn(Optional.of(ACCOUNT_ID));
        given(emailVerificationUseService.verifyPasswordResetOtp(
                CHALLENGE_ID,
                EMAIL,
                "123456"
        ))
                .willReturn(true);
        given(accountPasswordService.replacePasswordHashForReset(ACCOUNT_ID, NEW_PASSWORD))
                .willReturn(false);

        // When
        boolean reset = transaction.resetPassword(
                EMAIL,
                NEW_PASSWORD,
                CHALLENGE_ID,
                "123456"
        );

        // Then
        then(reset).isFalse();
        verify(revocationService, never()).revokeAllForAccount(
                ACCOUNT_ID,
                RefreshSessionRevocationReason.PASSWORD_RESET
        );
        verify(emailVerificationUseService, never()).consume(CHALLENGE_ID);
    }
}
