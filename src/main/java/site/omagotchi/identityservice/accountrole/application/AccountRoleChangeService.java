package site.omagotchi.identityservice.accountrole.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountLifecycleService;
import site.omagotchi.identityservice.account.application.result.AccountRoleChangeResult;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.accountrole.application.port.AccountRoleChangeAuditRepository;
import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAction;
import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAudit;
import site.omagotchi.identityservice.accountrole.domain.RecordedGlobalRole;
import site.omagotchi.identityservice.accountstate.application.AccountStateErrorCode;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeReason;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.util.UUID;

/**
 * 전역 역할 변경 Use Case.
 *
 * <p>역할 변경과 감사 저장을 하나의 트랜잭션으로 묶는다. 계정 행 잠금·마지막 관리자
 * 방어는 {@link AccountLifecycleService}가 담당한다.</p>
 *
 * <p>Refresh Session은 폐기하지 않는다. Refresh 회전이 계정 행에서 현재 역할을 다시
 * 읽어 Access Token을 발급하므로, 역할 변경은 다음 회전에 반영된다. 폐기해도 이미
 * 발급된 Access Token의 잔여 TTL은 막지 못하면서 재로그인 비용만 생긴다.</p>
 */
@Service
@RequiredArgsConstructor
public class AccountRoleChangeService {

    private final AccountLifecycleService accountLifecycleService;
    private final AccountRoleChangeAuditRepository auditRepository;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changeGlobalRole(
            UUID actorAccountId,
            UUID targetAccountId,
            AdminGlobalRole targetRole,
            String reason
    ) {
        // 계정 행 잠금 전 필수 사유 검증
        AccountStatusChangeReason normalizedReason = requireReason(reason);
        AccountRoleChangeResult result = accountLifecycleService.changeGlobalRoleByAdministrator(
                actorAccountId,
                targetAccountId,
                toDomainRole(targetRole)
        );

        // 동일 역할 요청의 후속 처리 생략
        if (!result.changed()) {
            return;
        }

        // 역할 변경과 같은 트랜잭션의 영속 감사 기록
        auditRepository.append(AccountRoleChangeAudit.record(
                actorAccountId,
                result.targetAccountId(),
                toAuditAction(result),
                toRecordedRole(result.before()),
                toRecordedRole(result.after()),
                normalizedReason,
                clock.instant()
        ));
    }

    // 사유 규칙은 계정 상태 변경과 동일하므로 값 객체와 에러 코드를 재사용한다.
    private AccountStatusChangeReason requireReason(String reason) {
        try {
            return new AccountStatusChangeReason(reason);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AccountStateErrorCode.INVALID_REASON, exception);
        }
    }

    private GlobalRole toDomainRole(AdminGlobalRole role) {
        return switch (role) {
            case USER -> GlobalRole.USER;
            case SYSTEM_ADMIN -> GlobalRole.SYSTEM_ADMIN;
        };
    }

    private AccountRoleChangeAction toAuditAction(AccountRoleChangeResult result) {
        if (result.granted()) {
            return AccountRoleChangeAction.ROLE_GRANTED;
        }
        if (result.revoked()) {
            return AccountRoleChangeAction.ROLE_REVOKED;
        }
        throw new IllegalArgumentException("감사 기록 대상 역할 전이가 올바르지 않습니다.");
    }

    private RecordedGlobalRole toRecordedRole(GlobalRole role) {
        return switch (role) {
            case USER -> RecordedGlobalRole.USER;
            case SYSTEM_ADMIN -> RecordedGlobalRole.SYSTEM_ADMIN;
        };
    }
}
