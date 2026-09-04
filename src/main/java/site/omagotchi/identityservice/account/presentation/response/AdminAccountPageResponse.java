package site.omagotchi.identityservice.account.presentation.response;

import site.omagotchi.identityservice.account.application.result.AdminAccountPageResult;
import site.omagotchi.identityservice.global.presentation.response.PageInfo;

import java.util.List;

public record AdminAccountPageResponse(
        List<AdminAccountResponse> items,
        PageInfo page
) {

    public AdminAccountPageResponse {
        items = List.copyOf(items);
    }

    public static AdminAccountPageResponse of(
            AdminAccountPageResult accountPage,
            int page,
            int size
    ) {
        return new AdminAccountPageResponse(
                accountPage.content().stream()
                        .map(AdminAccountResponse::from)
                        .toList(),
                new PageInfo(
                        page,
                        size,
                        accountPage.totalElements(),
                        totalPages(accountPage.totalElements(), size)
                )
        );
    }

    private static int totalPages(long totalElements, int size) {
        return (int) Math.min(Math.ceilDiv(totalElements, (long) size), Integer.MAX_VALUE);
    }
}
