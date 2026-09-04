package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.account.application.port.AccountStatusChangeAuditRepository;
import site.omagotchi.identityservice.account.domain.AccountStatusChangeAction;
import site.omagotchi.identityservice.account.domain.AccountStatusChangeAudit;

import java.time.Instant;
import java.util.UUID;

/** 계정 상태 변경과 로그인 잠금 해제 사건의 감사 기록 저장. */
@Component
@RequiredArgsConstructor
public class AccountStatusChangeAuditRecorder {

    private final AccountStatusChangeAuditRepository auditRepository;

    public void recordDisabled(UUID actorAccountId, UUID targetAccountId, String reason, Instant occurredAt) {
        append(actorAccountId, targetAccountId, AccountStatusChangeAction.ACCOUNT_DISABLED, reason, occurredAt);
    }

    public void recordReactivated(UUID actorAccountId, UUID targetAccountId, String reason, Instant occurredAt) {
        append(actorAccountId, targetAccountId, AccountStatusChangeAction.ACCOUNT_REACTIVATED, reason, occurredAt);
    }

    public void recordLoginLockReleased(UUID actorAccountId, UUID targetAccountId, String reason, Instant occurredAt) {
        append(actorAccountId, targetAccountId, AccountStatusChangeAction.LOGIN_LOCK_RELEASED, reason, occurredAt);
    }

    public void recordWithdrawal(UUID accountId, Instant occurredAt) {
        append(accountId, accountId, AccountStatusChangeAction.ACCOUNT_WITHDRAWN, "사용자 본인 탈퇴", occurredAt);
    }

    public void recordRecovery(UUID accountId, Instant occurredAt) {
        append(accountId, accountId, AccountStatusChangeAction.ACCOUNT_RECOVERED, "이메일 소유권 확인을 통한 계정 복구", occurredAt);
    }

    private void append(
            UUID actorAccountId,
            UUID targetAccountId,
            AccountStatusChangeAction action,
            String reason,
            Instant occurredAt
    ) {
        auditRepository.append(AccountStatusChangeAudit.create(
                actorAccountId,
                targetAccountId,
                action,
                reason,
                occurredAt
        ));
    }
}
