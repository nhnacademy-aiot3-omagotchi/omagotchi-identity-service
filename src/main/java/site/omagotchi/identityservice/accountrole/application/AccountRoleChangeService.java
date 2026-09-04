package site.omagotchi.identityservice.accountrole.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountAdministrationService;
import site.omagotchi.identityservice.accountrole.application.port.AccountRoleChangeAuditRepository;
import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAction;
import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAudit;

import java.time.Clock;
import java.util.UUID;

/**
 * 전역 역할 변경 유스케이스.
 *
 * <p>역할 변경과 감사 저장을 하나의 트랜잭션으로 묶는다. 계정 행 잠금·마지막 관리자
 * 방어는 {@link AccountAdministrationService}가 담당한다.</p>
 *
 * <p>Refresh Session은 폐기하지 않는다. Refresh 회전이 계정 행에서 현재 역할을 다시
 * 읽어 Access Token을 발급하므로, 역할 변경은 다음 회전에 반영된다. 폐기해도 이미
 * 발급된 Access Token의 잔여 TTL은 막지 못하면서 재로그인 비용만 생긴다.</p>
 */
@Service
@RequiredArgsConstructor
public class AccountRoleChangeService {

    private final AccountAdministrationService accountAdministrationService;
    private final AccountRoleChangeAuditRepository auditRepository;
    private final Clock clock;

    @Transactional
    public void changeGlobalRole(
            UUID actorAccountId,
            UUID targetAccountId,
            AdminGlobalRole targetRole,
            String reason
    ) {
        boolean changed = switch (targetRole) {
            case USER -> accountAdministrationService.revokeSystemAdministrator(
                    actorAccountId, targetAccountId);
            case SYSTEM_ADMIN -> accountAdministrationService.grantSystemAdministrator(
                    actorAccountId, targetAccountId);
        };

        // 동일 역할 요청의 후속 처리 생략
        if (!changed) {
            return;
        }

        // 역할 변경과 동일 트랜잭션의 감사 기록 저장
        AccountRoleChangeAction action = switch (targetRole) {
            case USER -> AccountRoleChangeAction.ROLE_REVOKED;
            case SYSTEM_ADMIN -> AccountRoleChangeAction.ROLE_GRANTED;
        };
        auditRepository.append(AccountRoleChangeAudit.create(
                actorAccountId,
                targetAccountId,
                action,
                reason,
                clock.instant()
        ));
    }
}
