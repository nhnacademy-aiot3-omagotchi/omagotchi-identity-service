package site.omagotchi.identityservice.account.presentation.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import site.omagotchi.identityservice.account.application.AccountAdminQueryService;
import site.omagotchi.identityservice.account.application.port.AccountSortOption;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;

/**
 * 관리자 사용자 목록의 조회 조건이다.
 *
 * <p>정렬 기준은 enum 화이트리스트로, 페이지 크기는 상한으로 고정해
 * 임의 컬럼 정렬과 대량 조회를 차단한다.
 */
public record AdminAccountSearchRequest(
        @Size(
                max = AccountAdminQueryService.KEYWORD_MAX_LENGTH,
                message = "검색어는 "
                        + AccountAdminQueryService.KEYWORD_MAX_LENGTH + "자 이하여야 합니다."
        )
        String query,
        AccountStatus status,
        GlobalRole role,
        @Min(value = 0, message = "page는 0 이상이어야 합니다.")
        Integer page,
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(
                value = AccountAdminQueryService.PAGE_SIZE_MAX,
                message = "size는 " + AccountAdminQueryService.PAGE_SIZE_MAX + " 이하여야 합니다."
        )
        Integer size,
        AccountSortOption sort
) {

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? AccountAdminQueryService.PAGE_SIZE_DEFAULT : size;
    }

    public AccountSortOption sortOrDefault() {
        return sort == null ? AccountSortOption.CREATED_AT_DESC : sort;
    }

    // 검색어 원문은 개인정보를 포함할 수 있어 기록 대상에서 제외한다.
    public int keywordLength() {
        return query == null ? 0 : query.length();
    }
}
