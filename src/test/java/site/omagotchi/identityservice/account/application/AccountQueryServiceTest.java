package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.application.port.AccountRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountQueryServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountQueryService accountQueryService =
            new AccountQueryService(accountRepository);

    @Test
    @DisplayName("계정 일괄 조회 요청의 식별자 중복 제거")
    void deduplicatesAccountIdsBeforeBatchLookup() {
        // Given
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        List<UUID> distinctIds = List.of(firstId, secondId);
        given(accountRepository.findAllById(distinctIds)).willReturn(List.of());

        // When
        var accounts = accountQueryService.findAllByIds(
                List.of(firstId, secondId, firstId)
        );

        // Then
        then(accounts).isEmpty();
        verify(accountRepository).findAllById(distinctIds);
    }
}
