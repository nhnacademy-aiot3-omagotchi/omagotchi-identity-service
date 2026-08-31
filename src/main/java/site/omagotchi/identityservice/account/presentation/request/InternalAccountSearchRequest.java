package site.omagotchi.identityservice.account.presentation.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Learning이 확정한 참여 가능 계정 범위 안에서만 검색하는 내부 요청이다. */
public record InternalAccountSearchRequest(
        String query,
        @NotEmpty(message = "candidateIds는 비어 있을 수 없습니다.")
        List<@NotNull(message = "candidateId는 null일 수 없습니다.") UUID> candidateIds
) {
}
