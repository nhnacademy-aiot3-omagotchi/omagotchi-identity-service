package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.PasswordPolicy;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Optional;
import java.util.UUID;

/**
 * 비밀번호 변경 대상 계정을 확인하고 현재 비밀번호 검증 뒤 저장된 Hash를 교체하는 Application 경계다.
 * 비밀번호 변경에 뒤따르는 Session 폐기 등 인증 수명주기는 호출자가 조정한다.
 */
@Service
@RequiredArgsConstructor
public class AccountPasswordService {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;

    @Transactional(readOnly = true)
    public String getPasswordChangeEmail(UUID accountId) {
        return requirePasswordChangeAllowed(accountRepository.findById(accountId)).getEmail();
    }

    @Transactional
    public String lockPasswordChangeEmail(UUID accountId) {
        return requirePasswordChangeAllowed(accountRepository.lockById(accountId)).getEmail();
    }

    @Transactional
    public void verifyAndReplacePasswordHash(
            UUID accountId,
            String currentRawPassword,
            String newRawPassword
    ) {
        Account account = requirePasswordChangeAllowed(accountRepository.lockById(accountId));
        if (!passwordHasher.matches(
                currentRawPassword == null ? "" : currentRawPassword,
                account.getPasswordHash()
        )) {
            throw new BusinessException(AccountErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (!PasswordPolicy.isSatisfiedBy(newRawPassword)) {
            throw new BusinessException(AccountErrorCode.INVALID_PASSWORD);
        }
        if (passwordHasher.matches(newRawPassword, account.getPasswordHash())) {
            throw new BusinessException(AccountErrorCode.PASSWORD_UNCHANGED);
        }

        account.changePasswordHash(passwordHasher.hash(newRawPassword));
    }

    private Account requirePasswordChangeAllowed(Optional<Account> account) {
        Account target = account.orElseThrow(
                () -> new BusinessException(AccountErrorCode.NOT_FOUND)
        );
        if (!target.isPasswordChangeAllowed()) {
            throw new BusinessException(AccountErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
        }
        return target;
    }
}
