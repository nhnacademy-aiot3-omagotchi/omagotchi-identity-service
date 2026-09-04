package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.application.result.AccountAuthenticationResult;
import site.omagotchi.identityservice.account.domain.Account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountAuthenticationServiceTest {

    @Test
    @DisplayName("없는 계정도 fallback Hash로 비밀번호 비교")
    void comparesMissingAccountPasswordAgainstFallbackHash() {
        // Given
        AccountRepository accountRepository = mock(AccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        given(accountRepository.lockByEmail("missing@example.com"))
                .willReturn(Optional.empty());
        given(passwordHasher.hash(anyString()))
                .willReturn("fallback-password-hash");
        given(passwordHasher.matches("raw-password", "fallback-password-hash"))
                .willReturn(false);

        AccountAuthenticationService accountAuthenticationService = new AccountAuthenticationService(
                accountRepository,
                passwordHasher,
                new LoginProtectionProperties(5, Duration.ofMinutes(10)),
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC)
        );

        // When
        Optional<AccountAuthenticationResult> result = accountAuthenticationService.authenticate(
                "missing@example.com",
                "raw-password"
        );

        // Then
        then(result).isEmpty();
        verify(passwordHasher).matches("raw-password", "fallback-password-hash");
    }

    @Test
    @DisplayName("비밀번호 불일치 시 실패 횟수를 기록하고 빈 Optional 반환")
    void recordsFailureWhenPasswordMismatches() {
        // Given
        AccountRepository accountRepository = mock(AccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        Account account = Account.register("user@example.com", "encoded-hash", "사용자");

        given(passwordHasher.hash(anyString())).willReturn("fallback-password-hash");
        given(accountRepository.lockByEmail("user@example.com"))
                .willReturn(Optional.of(account));
        given(passwordHasher.matches("wrong-password", "encoded-hash"))
                .willReturn(false);

        AccountAuthenticationService accountAuthenticationService = new AccountAuthenticationService(
                accountRepository,
                passwordHasher,
                new LoginProtectionProperties(5, Duration.ofMinutes(10)),
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC)
        );

        // When
        Optional<AccountAuthenticationResult> result = accountAuthenticationService.authenticate(
                "user@example.com",
                "wrong-password"
        );

        // Then
        then(result).isEmpty();
        then(account.getFailedLoginAttempts()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("비밀번호 일치 시 성공 기록과 인증 결과 반환")
    void recordsSuccessWhenPasswordMatches() {
        // Given
        AccountRepository accountRepository = mock(AccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        Account account = Account.register("user@example.com", "encoded-hash", "사용자");

        given(passwordHasher.hash(anyString())).willReturn("fallback-password-hash");
        given(accountRepository.lockByEmail("user@example.com"))
                .willReturn(Optional.of(account));
        given(passwordHasher.matches("correct-password", "encoded-hash"))
                .willReturn(true);

        AccountAuthenticationService accountAuthenticationService = new AccountAuthenticationService(
                accountRepository,
                passwordHasher,
                new LoginProtectionProperties(5, Duration.ofMinutes(10)),
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC)
        );

        // When
        Optional<AccountAuthenticationResult> result = accountAuthenticationService.authenticate(
                "user@example.com",
                "correct-password"
        );

        // Then
        then(result).isPresent();
        then(result.get().accountId()).isEqualTo(account.getId());
        then(result.get().globalRole()).isEqualTo(account.getGlobalRole().name());
        then(account.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("계정 조회 실패 시 예외 전파 (롤백 유도)")
    void propagatesExceptionWhenRepositoryFails() {
        // Given
        AccountRepository accountRepository = mock(AccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        given(passwordHasher.hash(anyString())).willReturn("fallback-password-hash");
        given(accountRepository.lockByEmail("user@example.com"))
                .willThrow(new IllegalStateException("데이터베이스 조회 실패"));

        AccountAuthenticationService accountAuthenticationService = new AccountAuthenticationService(
                accountRepository,
                passwordHasher,
                new LoginProtectionProperties(5, Duration.ofMinutes(10)),
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC)
        );

        // When
        org.assertj.core.api.ThrowableAssert.ThrowingCallable callable =
                () -> accountAuthenticationService.authenticate("user@example.com", "password");

        // Then
        then(org.assertj.core.api.BDDAssertions.catchThrowable(callable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("데이터베이스 조회 실패");
    }
}
