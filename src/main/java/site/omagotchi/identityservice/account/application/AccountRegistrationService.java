package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.PasswordPolicy;
import site.omagotchi.identityservice.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class AccountRegistrationService {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;

    @Transactional
    public Account signUp(String email, String rawPassword, String name) {
        // Identity가 소유하는 가입 정책별 공개 거절 Code
        if (!Account.isRegistrationEmailValid(email)) {
            throw new BusinessException(AccountErrorCode.INVALID_EMAIL);
        }
        if (!PasswordPolicy.isSatisfiedBy(rawPassword)) {
            throw new BusinessException(AccountErrorCode.INVALID_PASSWORD);
        }
        if (!Account.isRegistrationNameValid(name)) {
            throw new BusinessException(AccountErrorCode.INVALID_NAME);
        }

        String passwordHash = passwordHasher.hash(rawPassword);
        Account account = Account.register(
                email,
                passwordHash,
                name
        );
        return accountRepository.create(account);
    }
}
