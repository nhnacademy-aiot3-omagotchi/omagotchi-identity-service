package site.omagotchi.identityservice.accountaudit.presentation.response;

import site.omagotchi.identityservice.accountaudit.application.result.AccountPermissionAuditEntry;
import site.omagotchi.identityservice.accountaudit.application.result.AuditActor;

import java.time.Instant;
import java.util.UUID;

/**
 * 감사 한 줄의 응답.
 *
 * <p>문구는 만들지 않고 값만 내려보낸다. "무엇을 어떻게 보여줄지"는 화면의 몫이고,
 * 여기서 한국어 문장을 조립하면 다른 소비자가 다시 파싱해야 한다.</p>
 */
public record AdminAuditResponse(
        String auditType,
        String action,
        UUID actorUserId,
        String actorName,
        UUID targetUserId,
        String targetName,
        String beforeValue,
        String afterValue,
        String reason,
        Instant occurredAt
) {

    public static AdminAuditResponse from(AccountPermissionAuditEntry entry) {
        AuditActor actor = entry.actor();
        AuditActor target = entry.target();
        return new AdminAuditResponse(
                entry.auditType().name(),
                entry.action(),
                actor.userId(),
                actor.name(),
                target.userId(),
                target.name(),
                entry.beforeValue(),
                entry.afterValue(),
                entry.reason(),
                entry.occurredAt()
        );
    }
}
