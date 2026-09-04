package site.omagotchi.identityservice.account.application.result;

import java.util.List;

/** 관리자 계정 목록의 페이지 조회 결과. */
public record AdminAccountPageResult(
        List<AdminAccountResult> content,
        long totalElements
) {

    public AdminAccountPageResult {
        content = List.copyOf(content);
    }
}
