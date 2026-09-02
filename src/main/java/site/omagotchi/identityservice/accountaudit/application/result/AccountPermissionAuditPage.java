package site.omagotchi.identityservice.accountaudit.application.result;

import java.util.List;

public record AccountPermissionAuditPage(
        List<AccountPermissionAuditEntry> content,
        long totalElements
) {

    public AccountPermissionAuditPage {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
