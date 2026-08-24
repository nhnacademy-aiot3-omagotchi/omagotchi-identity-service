package site.omagotchi.identityservice.account.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.application.result.AccountAuthenticationResult;
import site.omagotchi.identityservice.account.domain.Account;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountAuthenticationService {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final LoginProtectionProperties loginProtectionProperties;
    private final Clock clock;
    private final String fallbackPasswordHash;

    public AccountAuthenticationService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            LoginProtectionProperties loginProtectionProperties,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.loginProtectionProperties = loginProtectionProperties;
        this.clock = clock;
        // 미가입 이메일에도 동일한 비밀번호 검증 비용을 적용하기 위한 임의 Hash
        // 응답 시간 차이에 의한 가입 이메일 추측 방지
        this.fallbackPasswordHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    @Transactional
    public Optional<AccountAuthenticationResult> authenticate(String email, String rawPassword) {
        Account account = accountRepository
                .lockByEmail(Account.normalizeEmail(email))
                .orElse(null);
        String passwordHash = account == null
                ? fallbackPasswordHash
                : account.getPasswordHash();
        boolean passwordMatches = passwordHasher.matches(
                rawPassword == null ? "" : rawPassword,
                passwordHash
        );

        if (account == null) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        account.recoverExpiredLoginLock(now);
        if (!account.isLoginAllowed()) {
            return Optional.empty();
        }
        if (!passwordMatches) {
            account.recordLoginFailure(
                    now,
                    loginProtectionProperties.maximumFailedAttempts(),
                    loginProtectionProperties.lockDuration()
            );
            return Optional.empty();
        }

        account.recordLoginSuccess();
        return Optional.of(AccountAuthenticationResult.from(account));
    }
}
