package site.omagotchi.identityservice.account.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.application.result.AccountAuthenticationResult;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AccountAuthenticationService {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final String fallbackPasswordHash;

    public AccountAuthenticationService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher
    ) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        // 미가입 이메일에도 동일한 비밀번호 검증 비용을 적용하기 위한 임의 Hash
        // 응답 시간 차이에 의한 가입 이메일 추측 방지
        this.fallbackPasswordHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    public Optional<AccountAuthenticationResult> authenticate(String email, String rawPassword) {
        Account account = accountRepository
                .findByEmail(Account.normalizeEmail(email))
                .orElse(null);
        String passwordHash = account == null
                ? fallbackPasswordHash
                : account.getPasswordHash();
        boolean passwordMatches = passwordHasher.matches(
                rawPassword == null ? "" : rawPassword,
                passwordHash
        );

        if (account == null || !account.isLoginAllowed() || !passwordMatches) {
            return Optional.empty();
        }
        return Optional.of(AccountAuthenticationResult.from(account));
    }

    public AccountAuthenticationResult getAuthenticationById(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(AccountAuthenticationResult::from)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.NOT_FOUND));
    }
}
