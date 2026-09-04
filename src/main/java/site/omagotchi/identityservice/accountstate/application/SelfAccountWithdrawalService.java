package site.omagotchi.identityservice.accountstate.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountLifecycleService;
import site.omagotchi.identityservice.account.application.AccountRecoveryPolicy;
import site.omagotchi.identityservice.account.application.AccountStatusChangeAuditRecorder;
import site.omagotchi.identityservice.account.application.result.AccountWithdrawalResult;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationReason;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationService;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SelfAccountWithdrawalService {

    private final AccountLifecycleService accountLifecycleService;
    private final AccountRecoveryPolicy accountRecoveryPolicy;
    private final AccountStatusChangeAuditRecorder accountStatusChangeAuditRecorder;
    private final RefreshSessionRevocationService refreshSessionRevocationService;

    // 탈퇴 상태 전이와 Refresh Session 폐기의 단일 트랜잭션
    @Transactional
    public Instant withdraw(UUID accountId, String currentRawPassword) {
        AccountWithdrawalResult result = accountLifecycleService.withdraw(
                accountId,
                currentRawPassword
        );
        // 중복 탈퇴 요청의 Refresh Session 재폐기 방지
        if (!result.changed()) {
            return accountRecoveryPolicy.recoveryDeadline(result.statusChangedAt());
        }

        // 실제 탈퇴 전이에 한정한 Refresh Session 폐기
        refreshSessionRevocationService.revokeAllForAccount(
                accountId,
                RefreshSessionRevocationReason.ACCOUNT_WITHDRAWN
        );
        accountStatusChangeAuditRecorder.recordWithdrawal(
                accountId, result.statusChangedAt());
        return accountRecoveryPolicy.recoveryDeadline(result.statusChangedAt());
    }
}
