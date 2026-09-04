package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import site.omagotchi.identityservice.account.application.port.AccountPage;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.AccountSearchCriteria;
import site.omagotchi.identityservice.account.application.port.AccountSortOption;
import site.omagotchi.identityservice.account.application.result.AdminAccountPageResult;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AccountAdminQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountRecoveryPolicy accountRecoveryPolicy =
            new AccountRecoveryPolicy(new AccountRecoveryProperties(
                    Instant.parse("2026-01-01T00:00:00Z")));
    private final AccountAdminQueryService accountAdminQueryService =
            new AccountAdminQueryService(
                    accountRepository,
                    accountRecoveryPolicy,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    @DisplayName("검색어 앞뒤 공백 제거 후 조회 조건 전달")
    void normalizesKeywordBeforeSearch() {
        // Given
        given(accountRepository.searchAccounts(any(), anyInt(), anyInt(), any()))
                .willReturn(new AccountPage(List.of(), 0));

        // When
        accountAdminQueryService.search(
                "  홍길동  ",
                AccountStatus.ACTIVE,
                true,
                GlobalRole.USER,
                0,
                20,
                AccountSortOption.NAME_ASC
        );

        // Then
        ArgumentCaptor<AccountSearchCriteria> criteria =
                ArgumentCaptor.forClass(AccountSearchCriteria.class);
        verify(accountRepository)
                .searchAccounts(criteria.capture(), anyInt(), anyInt(), any());
        then(criteria.getValue().keyword()).isEqualTo("홍길동");
        then(criteria.getValue().status()).isEqualTo(AccountStatus.ACTIVE);
        then(criteria.getValue().locked()).isTrue();
        then(criteria.getValue().role()).isEqualTo(GlobalRole.USER);
        then(criteria.getValue().checkedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("검색어 미지정은 조건 없는 전체 조회")
    void treatsNullKeywordAsNoCondition() {
        // Given
        given(accountRepository.searchAccounts(any(), anyInt(), anyInt(), any()))
                .willReturn(new AccountPage(List.of(), 0));

        // When
        accountAdminQueryService.search(null, null, null, null, 0, 20, null);

        // Then
        ArgumentCaptor<AccountSearchCriteria> criteria =
                ArgumentCaptor.forClass(AccountSearchCriteria.class);
        verify(accountRepository)
                .searchAccounts(criteria.capture(), anyInt(), anyInt(), any());
        then(criteria.getValue().keyword()).isNull();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("공백만 입력한 검색어는 전체 조회로 승격하지 않고 거부")
    void rejectsBlankKeyword(String keyword) {
        // When
        // Then
        thenThrownBy(() -> accountAdminQueryService.search(
                keyword, null, null, null, 0, 20, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("검색어 길이 상한 초과 거부")
    void rejectsTooLongKeyword() {
        // Given
        String keyword = "가".repeat(AccountAdminQueryService.KEYWORD_MAX_LENGTH + 1);

        // When
        // Then
        thenThrownBy(() -> accountAdminQueryService.search(
                keyword, null, null, null, 0, 20, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(accountRepository);
    }

    @ParameterizedTest(name = "page={0}, size={1}")
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, -1",
            "0, 101"
    })
    @DisplayName("Bean Validation을 우회한 페이지 범위 요청의 Application 경계 차단")
    void rejectsOutOfRangePaging(int page, int size) {
        // When
        // Then
        thenThrownBy(() -> accountAdminQueryService.search(
                null, null, null, null, page, size, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("정렬 미지정의 기본값은 최신 가입순")
    void appliesLatestCreatedSortByDefault() {
        // Given
        given(accountRepository.searchAccounts(any(), anyInt(), anyInt(), any()))
                .willReturn(new AccountPage(List.of(), 0));

        // When
        accountAdminQueryService.search(null, null, null, null, 0, 20, null);

        // Then
        ArgumentCaptor<AccountSortOption> sortOption =
                ArgumentCaptor.forClass(AccountSortOption.class);
        verify(accountRepository)
                .searchAccounts(any(), anyInt(), anyInt(), sortOption.capture());
        then(sortOption.getValue()).isEqualTo(AccountSortOption.CREATED_AT_DESC);
    }

    @Test
    @DisplayName("페이지 크기 상한값은 허용")
    void allowsMaximumPageSize() {
        // Given
        given(accountRepository.searchAccounts(any(), anyInt(), anyInt(), any()))
                .willReturn(new AccountPage(List.of(), 0));

        // When
        AdminAccountPageResult accountPage = accountAdminQueryService.search(
                null, null, null, null, 0, AccountAdminQueryService.PAGE_SIZE_MAX, null);

        // Then
        then(accountPage.totalElements()).isZero();
        verify(accountRepository).searchAccounts(
                any(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("탈퇴 계정 조회 결과에 상태 변경 시각과 복구 기한 포함")
    void returnsLifecycleTimestampsForWithdrawnAccount() {
        Instant withdrawnAt = Instant.parse("2026-09-01T00:00:00Z");
        Account account = Account.register(
                "withdrawn@example.com",
                "encoded-password",
                "탈퇴 사용자",
                Instant.parse("2026-08-01T00:00:00Z")
        );
        account.withdraw(withdrawnAt);
        given(accountRepository.searchAccounts(any(), anyInt(), anyInt(), any()))
                .willReturn(new AccountPage(List.of(account), 1));

        AdminAccountPageResult result = accountAdminQueryService.search(
                null, AccountStatus.WITHDRAWN, null, null, 0, 20, null);

        then(result.content()).singleElement().satisfies(item -> {
            then(item.status()).isEqualTo(AccountStatus.WITHDRAWN);
            then(item.locked()).isFalse();
            then(item.statusChangedAt()).isEqualTo(withdrawnAt);
            then(item.recoveryDeadline())
                    .isEqualTo(withdrawnAt.plus(AccountRecoveryPolicy.RECOVERY_WINDOW));
        });
    }
}
