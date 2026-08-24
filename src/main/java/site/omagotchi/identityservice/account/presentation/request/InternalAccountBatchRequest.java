package site.omagotchi.identityservice.account.presentation.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record InternalAccountBatchRequest(
        @NotEmpty(message = "accountIds는 비어 있을 수 없습니다.")
        @Size(
                max = MAX_ACCOUNT_IDS,
                message = "accountIds는 한 번에 " + MAX_ACCOUNT_IDS + "개까지 조회할 수 있습니다."
        )
        List<@NotNull(message = "accountId는 null일 수 없습니다.") UUID> accountIds
) {
    public static final int MAX_ACCOUNT_IDS = 100;
}
