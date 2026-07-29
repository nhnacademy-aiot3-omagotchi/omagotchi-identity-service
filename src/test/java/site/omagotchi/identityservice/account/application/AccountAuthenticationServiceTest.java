package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.application.result.AccountAuthenticationResult;

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
        given(accountRepository.findByEmail("missing@example.com"))
                .willReturn(Optional.empty());
        given(passwordHasher.hash(anyString()))
                .willReturn("fallback-password-hash");
        given(passwordHasher.matches("raw-password", "fallback-password-hash"))
                .willReturn(false);

        AccountAuthenticationService accountAuthenticationService = new AccountAuthenticationService(
                accountRepository,
                passwordHasher
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
}
