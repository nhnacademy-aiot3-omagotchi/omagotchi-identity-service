package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.EmailPolicy;
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
        String replacementPasswordHash = prepareReplacementPasswordHash(
                newRawPassword,
                account.getPasswordHash()
        ).orElseThrow(() -> new BusinessException(AccountErrorCode.PASSWORD_UNCHANGED));
        account.changePasswordHash(replacementPasswordHash);
    }

    // 이메일 형식을 검증 후, 정규화
    public String validateAndNormalizePasswordResetEmail(String email) {
        if (!EmailPolicy.isSatisfiedBy(email)) {
            throw new BusinessException(AccountErrorCode.INVALID_EMAIL);
        }
        return EmailPolicy.normalize(email);
    }

    // 비밀번호 정책 검증
    public void validatePasswordResetPassword(String newRawPassword) {
        validateNewPassword(newRawPassword);
    }

    /** 이메일에 해당하는 재설정 가능 계정을 잠그고 계정 식별자를 반환한다. */
    @Transactional
    public Optional<UUID> lockPasswordResetAccountId(String normalizedEmail) {
        return accountRepository.lockByEmail(normalizedEmail)
                .filter(Account::isManagementAllowed)
                .map(Account::getId);
    }

    /** 잠근 계정의 기존 비밀번호와 다른 새 해시로 교체하고 로그인 실패 상태를 초기화한다. */
    @Transactional
    public boolean replacePasswordHashForReset(UUID accountId, String newRawPassword) {
        Optional<Account> lockedAccount = accountRepository.lockById(accountId)
                .filter(Account::isManagementAllowed);
        if (lockedAccount.isEmpty()) {
            return false;
        }

        Account account = lockedAccount.get();
        Optional<String> replacementPasswordHash = prepareReplacementPasswordHash(
                newRawPassword,
                account.getPasswordHash()
        );
        if (replacementPasswordHash.isEmpty()) {
            return false;
        }

        account.resetPasswordHash(replacementPasswordHash.get());
        return true;
    }

    private Account requirePasswordChangeAllowed(Optional<Account> account) {
        Account target = account.orElseThrow(
                () -> new BusinessException(AccountErrorCode.NOT_FOUND)
        );
        if (!target.isManagementAllowed()) {
            throw new BusinessException(AccountErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
        }
        return target;
    }

    /** 새 비밀번호를 검증하고 현재 비밀번호와 다를 때만 해시를 준비한다. */
    private Optional<String> prepareReplacementPasswordHash(
            String newRawPassword,
            String currentPasswordHash
    ) {
        validateNewPassword(newRawPassword);
        if (passwordHasher.matches(newRawPassword, currentPasswordHash)) {
            return Optional.empty();
        }
        return Optional.of(passwordHasher.hash(newRawPassword));
    }

    /** 새 비밀번호가 공통 비밀번호 정책을 만족하지 않으면 비즈니스 오류로 거절한다. */
    private void validateNewPassword(String newRawPassword) {
        if (!PasswordPolicy.isSatisfiedBy(newRawPassword)) {
            throw new BusinessException(AccountErrorCode.INVALID_PASSWORD);
        }
    }
}
