package site.omagotchi.identityservice.auth.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.account.application.AccountQueryService;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;
import site.omagotchi.identityservice.email.application.EmailVerificationService;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class PasswordChangeServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000635"
    );
    private static final String EMAIL = "user@example.com";
    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";
    private static final String CHALLENGE_ID = "challenge-id";
    private static final String CODE = "123456";

    @Test
    @DisplayName("비밀번호 교체와 Session 폐기 후 PASSWORD_CHANGE OTP 소비")
    void consumesCodeAfterReplacingPasswordAndRevokingSessions() {
        Fixture fixture = fixture();
        given(fixture.accountPasswordService().verifyAndReplacePasswordHash(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
        )).willReturn(EMAIL);

        fixture.service().changePassword(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD,
                CHALLENGE_ID,
                CODE
        );

        InOrder order = inOrder(
                fixture.accountPasswordService(),
                fixture.revocationService(),
                fixture.emailVerificationService()
        );
        order.verify(fixture.accountPasswordService())
                .verifyAndReplacePasswordHash(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );
        order.verify(fixture.revocationService()).revokeAllForAccount(
                ACCOUNT_ID,
                RefreshSessionRevocationReason.PASSWORD_CHANGED
        );
        order.verify(fixture.emailVerificationService()).verifyAndConsumeCode(
                EMAIL,
                VerificationPurpose.PASSWORD_CHANGE,
                CHALLENGE_ID,
                CODE
        );
        verifyNoInteractions(fixture.accountQueryService());
        verifyNoMoreInteractions(
                fixture.accountPasswordService(),
                fixture.revocationService(),
                fixture.emailVerificationService()
        );
    }

    @Test
    @DisplayName("비밀번호 교체 실패 시 Session 폐기와 OTP 소비 생략")
    void preservesSessionsAndCodeWhenPasswordReplacementFails() {
        Fixture fixture = fixture();
        BusinessException failure = new BusinessException(
                AccountErrorCode.CURRENT_PASSWORD_MISMATCH
        );
        willThrow(failure).given(fixture.accountPasswordService())
                .verifyAndReplacePasswordHash(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        Throwable thrown = catchThrowable(() -> fixture.service().changePassword(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD,
                CHALLENGE_ID,
                CODE
        ));

        then(thrown).isSameAs(failure);
        verifyNoInteractions(
                fixture.revocationService(),
                fixture.accountQueryService(),
                fixture.emailVerificationService()
        );
    }

    @Test
    @DisplayName("비밀번호 변경 가능 계정의 이메일로 OTP 발급·발송 요청")
    void requestsEmailOtpForAuthenticatedAccount() {
        Fixture fixture = fixture();
        Account account = Account.register(EMAIL, "password-hash", "사용자");
        EmailVerificationChallengeResult expected =
                new EmailVerificationChallengeResult(CHALLENGE_ID, 600L);
        given(fixture.accountQueryService().getById(ACCOUNT_ID)).willReturn(account);
        given(fixture.emailVerificationService().requestCode(
                EMAIL,
                VerificationPurpose.PASSWORD_CHANGE
        )).willReturn(expected);

        then(fixture.service().requestEmailOtp(ACCOUNT_ID))
                .isSameAs(expected);

        verify(fixture.accountQueryService()).getById(ACCOUNT_ID);
        verify(fixture.emailVerificationService()).requestCode(
                EMAIL,
                VerificationPurpose.PASSWORD_CHANGE
        );
        verifyNoInteractions(
                fixture.accountPasswordService(),
                fixture.revocationService()
        );
    }

    @Test
    @DisplayName("비밀번호 변경 불가 계정이면 OTP를 발급하지 않음")
    void rejectsUnavailableAccountBeforeIssuingCode() {
        Fixture fixture = fixture();
        Account account = mock(Account.class);
        given(fixture.accountQueryService().getById(ACCOUNT_ID)).willReturn(account);
        given(account.isPasswordChangeAllowed()).willReturn(false);

        Throwable thrown = catchThrowable(() -> fixture.service()
                .requestEmailOtp(ACCOUNT_ID));

        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isSameAs(AccountErrorCode.PASSWORD_CHANGE_NOT_ALLOWED)
        );
        verifyNoInteractions(
                fixture.accountPasswordService(),
                fixture.revocationService(),
                fixture.emailVerificationService()
        );
    }

    private Fixture fixture() {
        AccountPasswordService accountPasswordService = mock(AccountPasswordService.class);
        RefreshSessionRevocationService revocationService = mock(
                RefreshSessionRevocationService.class
        );
        AccountQueryService accountQueryService = mock(AccountQueryService.class);
        EmailVerificationService emailVerificationService = mock(
                EmailVerificationService.class
        );
        return new Fixture(
                accountPasswordService,
                revocationService,
                accountQueryService,
                emailVerificationService,
                new PasswordChangeService(
                        accountPasswordService,
                        revocationService,
                        accountQueryService,
                        emailVerificationService
                )
        );
    }

    private record Fixture(
            AccountPasswordService accountPasswordService,
            RefreshSessionRevocationService revocationService,
            AccountQueryService accountQueryService,
            EmailVerificationService emailVerificationService,
            PasswordChangeService service
    ) {
    }
}
