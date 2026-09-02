package site.omagotchi.identityservice.accountaudit.presentation.response;

import site.omagotchi.identityservice.accountaudit.application.result.AccountPermissionAuditPage;
import site.omagotchi.identityservice.global.presentation.response.PageInfo;

import java.util.List;

public record AdminAuditPageResponse(
        List<AdminAuditResponse> items,
        PageInfo page
) {

    public AdminAuditPageResponse {
        items = List.copyOf(items);
    }

    public static AdminAuditPageResponse of(
            AccountPermissionAuditPage auditPage,
            int page,
            int size
    ) {
        return new AdminAuditPageResponse(
                auditPage.content().stream().map(AdminAuditResponse::from).toList(),
                new PageInfo(
                        page,
                        size,
                        auditPage.totalElements(),
                        totalPages(auditPage.totalElements(), size)
                )
        );
    }

    // size는 Application 경계에서 1 이상으로 검증되지만 0 나눗셈은 방어적으로 차단한다.
    private static int totalPages(long totalElements, int size) {
        if (size < 1) {
            return 0;
        }
        return (int) Math.min(Math.ceilDiv(totalElements, (long) size), Integer.MAX_VALUE);
    }
}
