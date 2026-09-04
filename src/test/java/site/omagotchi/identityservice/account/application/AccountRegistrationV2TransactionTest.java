package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.account.application.result.AccountRegistrationResult;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.emailverification.application.AccountRecoveryEmailOtpService;
import site.omagotchi.identityservice.emailverification.application.SignupEmailOtpService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
    private AccountLifecycleService accountLifecycleService;
    @Mock
    private SignupEmailOtpService emailOtpService;
    @Mock
    private AccountRecoveryEmailOtpService recoveryEmailOtpService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountRecoveryPolicy recoveryPolicy;
    @Mock
    private AccountStatusChangeAuditRecorder accountStatusChangeAuditRecorder;

    private AccountRegistrationV2Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new AccountRegistrationV2Transaction(
                registrationService,
                accountLifecycleService,
                accountRepository,
                emailOtpService,
                recoveryEmailOtpService,
                recoveryPolicy,
                accountStatusChangeAuditRecorder,
                Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("인증 실패는 업무 변경 없이 결과로 반환")
    void returnsFailureWithoutRegistration() {
        // Given
        UUID challengeId = CHALLENGE_ID;
        given(accountRepository.lockByEmail("member@example.com"))
                .willReturn(Optional.empty());
        given(emailOtpService.verify(
                challengeId,
                "member@example.com",
                "000000"
        )).willReturn(false);

        // When
        Optional<AccountRegistrationResult> result = transaction.signUp(
                " Member@Example.com ",
                "long-enough-password",
                "member",
                challengeId,
                "000000"
        );

        // Then
        then(result).isEmpty();
        verify(registrationService).validateRegistrationInput(
                " Member@Example.com ", "long-enough-password", "member"
        );
        verify(registrationService, never()).signUp(
                " Member@Example.com ", "long-enough-password", "member"
        );
        verify(emailOtpService, never()).consume(challengeId);
    }

    @Test
    @DisplayName("인증 성공 시 계정 생성 후 Challenge 소비")
    void registersAndConsumesChallenge() {
        // Given
        UUID challengeId = CHALLENGE_ID;
        given(accountRepository.lockByEmail("member@example.com"))
                .willReturn(Optional.empty());
        Account account = Account.register(
                "member@example.com",
                "password-hash",
                "member",
                Instant.EPOCH
        );
        given(emailOtpService.verify(
                challengeId,
                "member@example.com",
                "123456"
        )).willReturn(true);
        given(registrationService.signUp(
                "member@example.com", "long-enough-password", "member"
        )).willReturn(account);

        // When
        AccountRegistrationResult result = transaction.signUp(
                "member@example.com",
                "long-enough-password",
                "member",
                challengeId,
                "123456"
        ).orElseThrow();

        // Then
        then(result.account()).isSameAs(account);
        then(result.outcome()).isEqualTo(AccountRegistrationResult.Outcome.CREATED);
        verify(emailOtpService).consume(challengeId);
    }
}
