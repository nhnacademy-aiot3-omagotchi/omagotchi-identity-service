package site.omagotchi.identityservice.accountstate.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountLifecycleService;
import site.omagotchi.identityservice.account.application.result.AccountStateChangeResult;
import site.omagotchi.identityservice.account.application.result.AccountStateValue;
import site.omagotchi.identityservice.accountstate.application.port.AccountStatusChangeAuditRepository;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeAction;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeAudit;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeReason;
import site.omagotchi.identityservice.accountstate.domain.RecordedAccountStatus;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationReason;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationService;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAccountStatusChangeService {

    private final AccountLifecycleService accountLifecycleService;
    private final RefreshSessionRevocationService refreshSessionRevocationService;
    private final AccountStatusChangeAuditRepository auditRepository;
    private final Clock clock;

    // 상태 변경·세션 폐기·감사 저장의 단일 트랜잭션
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changeStatus(
            UUID actorAccountId,
            UUID targetAccountId,
            AdminAccountStatus targetStatus,
            String reason
    ) {
        // 계정 행 잠금 전 필수 사유 검증
        AccountStatusChangeReason normalizedReason = requireReason(reason);
        AccountStateChangeResult result = switch (targetStatus) {
            case ACTIVE -> accountLifecycleService.activateByAdministrator(
                    actorAccountId,
                    targetAccountId
            );
            case DISABLED -> accountLifecycleService.disableByAdministrator(
                    actorAccountId,
                    targetAccountId
            );
        };

        // 동일 상태 요청의 후속 처리 생략
        if (!result.changed()) {
            return;
        }
        // 실제 DISABLED 전이에 한정한 Refresh Session 폐기
        if (result.disabled()) {
            refreshSessionRevocationService.revokeAllForAccount(
                    targetAccountId,
                    RefreshSessionRevocationReason.ACCOUNT_DISABLED
            );
        }

        // 상태 변경과 같은 트랜잭션의 영속 감사 기록
        auditRepository.append(AccountStatusChangeAudit.record(
                actorAccountId,
                result.targetAccountId(),
                toAuditAction(result),
                toRecordedStatus(result.before()),
                toRecordedStatus(result.after()),
                normalizedReason,
                clock.instant()
        ));
    }

    private AccountStatusChangeReason requireReason(String reason) {
        try {
            return new AccountStatusChangeReason(reason);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AccountStateErrorCode.INVALID_REASON, exception);
        }
    }

    private AccountStatusChangeAction toAuditAction(AccountStateChangeResult result) {
        if (result.disabled()) {
            return AccountStatusChangeAction.ACCOUNT_DISABLED;
        }
        if (result.unlocked()) {
            return AccountStatusChangeAction.ACCOUNT_UNLOCKED;
        }
        if (result.reactivated()) {
            return AccountStatusChangeAction.ACCOUNT_REACTIVATED;
        }
        throw new IllegalArgumentException("감사 기록 대상 상태 전이가 올바르지 않습니다.");
    }

    private RecordedAccountStatus toRecordedStatus(AccountStateValue status) {
        return switch (status) {
            case ACTIVE -> RecordedAccountStatus.ACTIVE;
            case LOCKED -> RecordedAccountStatus.LOCKED;
            case DISABLED -> RecordedAccountStatus.DISABLED;
            case WITHDRAWN -> RecordedAccountStatus.WITHDRAWN;
        };
    }
}
