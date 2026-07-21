package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountErrorCode;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountReader {

    private final AccountJpaRepository accountJpaRepository;

    public Account readById(Long userId) {
        return accountJpaRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.NOT_FOUND));
    }

    public Optional<Account> findByEmail(String email) {
        return accountJpaRepository.findByEmail(Account.normalizeEmail(email));
    }

    public void ensureEmailAvailable(String email) {
        if (accountJpaRepository.existsByEmail(Account.normalizeEmail(email))) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_EMAIL);
        }
    }
}
