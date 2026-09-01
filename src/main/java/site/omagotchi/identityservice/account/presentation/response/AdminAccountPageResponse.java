package site.omagotchi.identityservice.account.presentation.response;

import site.omagotchi.identityservice.account.application.port.AccountPage;
import site.omagotchi.identityservice.global.presentation.response.PageInfo;

import java.util.List;

public record AdminAccountPageResponse(
        List<AdminAccountResponse> items,
        PageInfo page
) {

    public AdminAccountPageResponse {
        items = List.copyOf(items);
    }

    public static AdminAccountPageResponse of(AccountPage accountPage, int page, int size) {
        return new AdminAccountPageResponse(
                accountPage.content().stream().map(AdminAccountResponse::from).toList(),
                new PageInfo(
                        page,
                        size,
                        accountPage.totalElements(),
                        totalPages(accountPage.totalElements(), size)
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
