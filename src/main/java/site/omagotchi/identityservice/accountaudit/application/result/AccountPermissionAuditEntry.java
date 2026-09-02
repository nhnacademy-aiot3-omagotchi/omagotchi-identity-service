package site.omagotchi.identityservice.accountaudit.application.result;

import site.omagotchi.identityservice.accountaudit.domain.AccountPermissionChangeAuditType;

import java.time.Instant;

/** 화면 한 줄에 대응하는 감사 기록. */
public record AccountPermissionAuditEntry(
        AccountPermissionChangeAuditType auditType,
        String action,
        AuditActor actor,
        AuditActor target,
        String beforeValue,
        String afterValue,
        String reason,
        Instant occurredAt
) {
}
