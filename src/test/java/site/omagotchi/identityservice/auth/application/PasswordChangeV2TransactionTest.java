package site.omagotchi.identityservice.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationUseService;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordChangeV2TransactionTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000701101"
    );
    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000701102"
    );

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountPasswordService accountPasswordService;
    @Mock
    private RefreshSessionRevocationService revocationService;
    @Mock
    private EmailVerificationUseService verificationUseService;

    private PasswordChangeV2Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new PasswordChangeV2Transaction(
                accountRepository,
                accountPasswordService,
                revocationService,
                verificationUseService
        );
        given(accountRepository.lockById(ACCOUNT_ID)).willReturn(Optional.of(Account.register(
                "member@example.com", "password-hash", "member"
        )));
    }

    @Test
    @DisplayName("OTP 실패 시 실패 결과만 반환하고 비밀번호·Session 미변경")
    void keepsBusinessStateWhenOtpFails() {
        // Given
        given(verificationUseService.verify(
                CHALLENGE_ID,
                "member@example.com",
                EmailVerificationPurpose.PASSWORD_CHANGE,
                "000000"
        )).willReturn(false);

        // When
        boolean changed = transaction.changePassword(
                ACCOUNT_ID, "current-password", "new-long-password", CHALLENGE_ID, "000000"
        );

        // Then
        then(changed).isFalse();
        verify(accountPasswordService, never()).verifyAndReplacePasswordHash(
                ACCOUNT_ID, "current-password", "new-long-password"
        );
        verify(revocationService, never()).revokeAllForAccount(
                ACCOUNT_ID, RefreshSessionRevocationReason.PASSWORD_CHANGED
        );
        verify(verificationUseService, never()).consume(CHALLENGE_ID);
    }

    @Test
    @DisplayName("OTP 성공 시 비밀번호 변경·Session 폐기·Challenge 소비")
    void changesPasswordRevokesSessionsAndConsumesOtp() {
        // Given
        given(verificationUseService.verify(
                CHALLENGE_ID,
                "member@example.com",
                EmailVerificationPurpose.PASSWORD_CHANGE,
                "123456"
        )).willReturn(true);

        // When
        boolean changed = transaction.changePassword(
                ACCOUNT_ID, "current-password", "new-long-password", CHALLENGE_ID, "123456"
        );

        // Then
        then(changed).isTrue();
        verify(accountPasswordService).verifyAndReplacePasswordHash(
                ACCOUNT_ID, "current-password", "new-long-password"
        );
        verify(revocationService).revokeAllForAccount(
                ACCOUNT_ID, RefreshSessionRevocationReason.PASSWORD_CHANGED
        );
        verify(verificationUseService).consume(CHALLENGE_ID);
    }
}
