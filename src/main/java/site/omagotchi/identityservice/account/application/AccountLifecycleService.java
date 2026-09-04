package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.application.result.AccountWithdrawalResult;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

    private final AccountRepository accountRepository;
    private final SystemAdministratorReductionGuard administratorReductionGuard;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Transactional
    public AccountWithdrawalResult withdraw(
            UUID accountId,
            String currentRawPassword
    ) {
        Account account = accountRepository.lockById(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.NOT_FOUND));

        // 이미 탈퇴한 계정의 멱등 처리
        if (account.getStatus() == AccountStatus.WITHDRAWN) {
            return new AccountWithdrawalResult(false, account.getStatusChangedAt());
        }
        if (!account.isWithdrawalAllowed()) {
            throw new BusinessException(AccountErrorCode.WITHDRAWAL_NOT_ALLOWED);
        }
        if (!passwordHasher.matches(
                currentRawPassword == null ? "" : currentRawPassword,
                account.getPasswordHash()
        )) {
            throw new BusinessException(AccountErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        // 계정 행 잠금 뒤 마지막 ACTIVE SYSTEM_ADMIN 보존 검증
        administratorReductionGuard.requireAnotherActiveAdministrator(account);
        boolean changed = account.withdraw(clock.instant());
        return new AccountWithdrawalResult(changed, account.getStatusChangedAt());
    }

    void recover(Account account, String rawPassword, String name, Instant recoveredAt) {
        account.recover(passwordHasher.hash(rawPassword), name, recoveredAt);
    }
}
