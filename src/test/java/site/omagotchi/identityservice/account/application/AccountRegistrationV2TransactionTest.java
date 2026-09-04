package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.account.application.result.AccountRegistrationAttempt;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationUseService;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountRegistrationV2TransactionTest {

    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700501"
    );

    @Mock
    private AccountRegistrationService registrationService;
    @Mock
    private EmailVerificationUseService emailVerificationUseService;

    private AccountRegistrationV2Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new AccountRegistrationV2Transaction(
                registrationService,
                emailVerificationUseService
        );
    }

    @Test
    @DisplayName("인증 실패는 업무 변경 없이 결과로 반환")
    void returnsFailureWithoutRegistration() {
        // Given
        UUID challengeId = CHALLENGE_ID;
        given(emailVerificationUseService.verifySignupOtp(
                challengeId,
                "member@example.com",
                "000000"
        )).willReturn(false);

        // When
        AccountRegistrationAttempt attempt = transaction.signUp(
                " Member@Example.com ",
                "long-enough-password",
                "member",
                challengeId,
                "000000"
        );

        // Then
        then(attempt.emailVerified()).isFalse();
        verify(registrationService).validateRegistrationInput(
                " Member@Example.com ", "long-enough-password", "member"
        );
        verify(registrationService, never()).signUp(
                " Member@Example.com ", "long-enough-password", "member"
        );
        verify(emailVerificationUseService, never()).consume(challengeId);
    }

    @Test
    @DisplayName("인증 성공 시 계정 생성 후 Challenge 소비")
    void registersAndConsumesChallenge() {
        // Given
        UUID challengeId = CHALLENGE_ID;
        Account account = Account.register(
                "member@example.com", "password-hash", "member"
        );
        given(emailVerificationUseService.verifySignupOtp(
                challengeId,
                "member@example.com",
                "123456"
        )).willReturn(true);
        given(registrationService.signUp(
                "member@example.com", "long-enough-password", "member"
        )).willReturn(account);

        // When
        AccountRegistrationAttempt attempt = transaction.signUp(
                "member@example.com",
                "long-enough-password",
                "member",
                challengeId,
                "123456"
        );

        // Then
        then(attempt.account()).isSameAs(account);
        verify(emailVerificationUseService).consume(challengeId);
    }
}
