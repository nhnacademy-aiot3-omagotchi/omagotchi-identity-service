package site.omagotchi.identityservice.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.auth.application.result.PasswordResetEmailOtpResult;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationIssueService;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000702301"
    );
    private static final String EMAIL = "member@example.com";
    private static final String NEW_PASSWORD = "new-password-passphrase";

    @Mock
    private PasswordResetTransaction transaction;
    @Mock
    private AccountPasswordService accountPasswordService;
    @Mock
    private EmailVerificationIssueService emailVerificationIssueService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                transaction,
                accountPasswordService,
                emailVerificationIssueService
        );
        given(accountPasswordService.validateAndNormalizePasswordResetEmail(EMAIL))
                .willReturn(EMAIL);
    }

    @Test
    @DisplayName("계정 조회 없이 비밀번호 재설정 OTP 발급")
    void issuesPasswordResetOtpWithoutAccountLookup() {
        // Given
        given(emailVerificationIssueService.issuePasswordResetOtp(EMAIL))
                .willReturn(new IssuedEmailVerification(CHALLENGE_ID, 300));

        // When
        PasswordResetEmailOtpResult result = service.issueEmailOtp(EMAIL);

        // Then
        then(result).isEqualTo(new PasswordResetEmailOtpResult(CHALLENGE_ID, 300));
        verify(emailVerificationIssueService).issuePasswordResetOtp(EMAIL);
        verify(accountPasswordService, never()).lockPasswordResetAccountId(EMAIL);
    }

    @Test
    @DisplayName("계정·OTP 실패는 하나의 공개 오류로 변환")
    void mapsResetFailureToGenericError() {
        // Given
        given(transaction.resetPassword(EMAIL, NEW_PASSWORD, CHALLENGE_ID, "123456"))
                .willReturn(false);

        // When
        // Then
        thenThrownBy(() -> service.resetPassword(
                EMAIL,
                NEW_PASSWORD,
                CHALLENGE_ID,
                "123456"
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_PASSWORD_RESET)
        );
    }

}
