package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.PasswordPolicy;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

/**
 * 계정의 현재 비밀번호를 검증하고 저장된 비밀번호 Hash를 교체하는 Application 경계다.
 * 비밀번호 변경에 뒤따르는 Session 폐기 등 인증 수명주기는 호출자가 조정한다.
 */
@Service
@RequiredArgsConstructor
public class AccountPasswordService {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;

    @Transactional
    public String verifyAndReplacePasswordHash(
            UUID accountId,
            String currentRawPassword,
            String newRawPassword
    ) {
        Account account = accountRepository.lockById(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.NOT_FOUND));

        if (!account.isPasswordChangeAllowed()) {
            throw new BusinessException(AccountErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
        }
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
        return account.getEmail();
    }
}
