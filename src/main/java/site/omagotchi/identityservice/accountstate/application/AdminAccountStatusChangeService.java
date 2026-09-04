package site.omagotchi.identityservice.accountstate.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountAdministrationService;
import site.omagotchi.identityservice.account.application.AccountStatusChangeAuditRecorder;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationReason;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAccountStatusChangeService {

    private final AccountAdministrationService accountAdministrationService;
    private final AccountStatusChangeAuditRecorder accountStatusChangeAuditRecorder;
    private final RefreshSessionRevocationService refreshSessionRevocationService;

    // 상태 변경·세션 폐기·감사 저장의 단일 트랜잭션
    @Transactional
    public void changeStatus(
            UUID actorAccountId,
            UUID targetAccountId,
            AdminAccountStatus targetStatus,
            String reason
    ) {
        Optional<Instant> changedAt = switch (targetStatus) {
            case ACTIVE -> accountAdministrationService.activate(
                    actorAccountId,
                    targetAccountId
            );
            case DISABLED -> accountAdministrationService.disable(
                    actorAccountId,
                    targetAccountId
            );
        };

        // 동일 상태 요청의 후속 처리 생략
        if (changedAt.isEmpty()) {
            return;
        }
        // 실제 DISABLED 전이에 한정한 Refresh Session 폐기
        if (targetStatus == AdminAccountStatus.DISABLED) {
            refreshSessionRevocationService.revokeAllForAccount(
                    targetAccountId,
                    RefreshSessionRevocationReason.ACCOUNT_DISABLED
            );
        }

        // 상태 변경과 동일 트랜잭션의 감사 기록 저장
        Instant occurredAt = changedAt.get();
        switch (targetStatus) {
            case ACTIVE -> accountStatusChangeAuditRecorder.recordReactivated(
                    actorAccountId,
                    targetAccountId,
                    reason,
                    occurredAt
            );
            case DISABLED -> accountStatusChangeAuditRecorder.recordDisabled(
                    actorAccountId,
                    targetAccountId,
                    reason,
                    occurredAt
            );
        }
    }
}
