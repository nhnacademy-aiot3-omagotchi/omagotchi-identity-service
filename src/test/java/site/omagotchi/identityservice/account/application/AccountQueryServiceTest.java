package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import site.omagotchi.identityservice.global.exception.BusinessException;

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

    @Test
    @DisplayName("계정 검색어 앞뒤 공백 제거와 결과 상한 적용")
    void normalizesAccountSearchQuery() {
        // Given
        Account account = mock(Account.class);
        UUID accountId = UUID.randomUUID();
        given(accountRepository.searchByNameOrEmail(
                "사용자@example.com",
                List.of(accountId),
                AccountQueryService.ACCOUNT_SEARCH_LIMIT
        )).willReturn(List.of(account));

        // When
        var accounts = accountQueryService.searchByNameOrEmail(
                "  사용자@example.com  ", List.of(accountId));

        // Then
        then(accounts).containsExactly(account);
        verify(accountRepository).searchByNameOrEmail(
                "사용자@example.com",
                List.of(accountId),
                AccountQueryService.ACCOUNT_SEARCH_LIMIT
        );
    }

    @Test
    @DisplayName("Unicode 공백을 검색어 양끝에서 제거한다")
    void stripsUnicodeWhitespaceFromAccountSearchQuery() {
        UUID accountId = UUID.randomUUID();
        given(accountRepository.searchByNameOrEmail("사용자", List.of(accountId), AccountQueryService.ACCOUNT_SEARCH_LIMIT))
                .willReturn(List.of());

        accountQueryService.searchByNameOrEmail("\u3000사용자\u3000", List.of(accountId));

        verify(accountRepository).searchByNameOrEmail("사용자", List.of(accountId), AccountQueryService.ACCOUNT_SEARCH_LIMIT);
    }

    @Test
    @DisplayName("Unicode 공백만 있는 검색어와 후보 상한 초과 요청은 저장소를 호출하지 않는다")
    void rejectsInvalidSearchBeforeRepositoryLookup() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> accountQueryService.searchByNameOrEmail("\u3000", List.of(accountId)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> accountQueryService.searchByNameOrEmail(
                "사용자", java.util.stream.IntStream.rangeClosed(0, AccountQueryService.ACCOUNT_SEARCH_CANDIDATE_IDS_MAX)
                        .mapToObj(ignored -> UUID.randomUUID()).toList()))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(accountRepository);
    }
}
