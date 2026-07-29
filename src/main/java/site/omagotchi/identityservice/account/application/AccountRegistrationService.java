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
        if (!PasswordPolicy.isSatisfiedBy(rawPassword)
                || !Account.isRegistrationDetailsValid(email, name)) {
            throw new BusinessException(AccountErrorCode.INVALID_SIGNUP_INPUT);
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
