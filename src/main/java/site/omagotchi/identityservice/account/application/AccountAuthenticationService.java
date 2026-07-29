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
        // 계정이 없어도 동일한 비밀번호 Hash 검증을 수행하기 위한 임의의 Hash
        // 계정 유무에 따라 검증을 생략하면 응답 시간이 달라져 가입된 이메일을 추측하기 쉬워짐
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
