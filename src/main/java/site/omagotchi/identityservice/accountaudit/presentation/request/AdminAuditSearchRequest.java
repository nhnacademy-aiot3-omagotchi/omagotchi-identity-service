package site.omagotchi.identityservice.accountaudit.presentation.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import site.omagotchi.identityservice.accountaudit.application.AccountAuditQueryService;

public record AdminAuditSearchRequest(

        @Min(value = 0, message = "page는 0 이상이어야 합니다.")
        Integer page,

        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = AccountAuditQueryService.PAGE_SIZE_MAX, message = "size는 100 이하여야 합니다.")
        Integer size
) {

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? AccountAuditQueryService.PAGE_SIZE_DEFAULT : size;
    }
}
