package site.omagotchi.identityservice.accountaudit.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.accountaudit.application.port.AccountPermissionChangeAuditPage;
import site.omagotchi.identityservice.accountaudit.application.port.AccountPermissionChangeAuditRepository;
import site.omagotchi.identityservice.accountaudit.application.result.AccountPermissionAuditPage;
import site.omagotchi.identityservice.accountaudit.domain.AccountPermissionChangeAudit;
import site.omagotchi.identityservice.accountaudit.domain.AccountPermissionChangeAuditType;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AccountAuditQueryServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID TARGET_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-02T05:03:00Z");

    @Test
    @DisplayName("감사 행의 계정 이름을 한 번에 모아 채운다")
    void resolvesAccountNamesInSingleLookup() {
        // Given: 같은 두 사람이 등장하는 감사 두 줄
        Fixture fixture = fixture();
        // 헬퍼가 내부에서 stubbing 하므로 given(...) 인자 안에서 부르면 안 된다.
        // 인자가 먼저 평가되면서 바깥 stubbing 이 끝나기 전에 새 stubbing 이 시작된다.
        List<AccountPermissionChangeAudit> audits = List.of(audit(), audit());
        List<Account> accounts = List.of(
                account(ACTOR_ID, "시스템 관리자"),
                account(TARGET_ID, "문재민")
        );
        given(fixture.auditRepository().findRecent(0, 20))
                .willReturn(new AccountPermissionChangeAuditPage(audits, 2));
        given(fixture.accountRepository().findAllById(Set.of(ACTOR_ID, TARGET_ID)))
                .willReturn(accounts);

        // When
        AccountPermissionAuditPage page = fixture.service().findRecent(0, 20);

        // Then: 행마다 조회하면 페이지당 최대 2N 번이 된다. 중복을 제거해 한 번만 부른다.
        verify(fixture.accountRepository()).findAllById(Set.of(ACTOR_ID, TARGET_ID));
        thenSoftly(softly -> {
            softly.then(page.content()).hasSize(2);
            softly.then(page.content().get(0).actor().name()).isEqualTo("시스템 관리자");
            softly.then(page.content().get(0).target().name()).isEqualTo("문재민");
            softly.then(page.content().get(0).action()).isEqualTo("ROLE_GRANTED");
            softly.then(page.content().get(0).beforeValue()).isEqualTo("USER");
            softly.then(page.content().get(0).afterValue()).isEqualTo("SYSTEM_ADMIN");
            softly.then(page.totalElements()).isEqualTo(2L);
        });
    }

    @Test
    @DisplayName("계정 조회가 비어도 감사 한 줄을 잃지 않는다")
    void keepsAuditRowWhenAccountLookupMisses() {
        // Given: 계정이 하나도 조회되지 않는 상황
        Fixture fixture = fixture();
        List<AccountPermissionChangeAudit> audits = List.of(audit());
        given(fixture.auditRepository().findRecent(0, 20))
                .willReturn(new AccountPermissionChangeAuditPage(audits, 1));
        given(fixture.accountRepository().findAllById(Set.of(ACTOR_ID, TARGET_ID)))
                .willReturn(List.of());

        // When
        AccountPermissionAuditPage page = fixture.service().findRecent(0, 20);

        // Then: 이름만 비고 누가 했는지는 UUID 가 남는다
        thenSoftly(softly -> {
            softly.then(page.content()).hasSize(1);
            softly.then(page.content().get(0).actor().name()).isNull();
            softly.then(page.content().get(0).actor().userId()).isEqualTo(ACTOR_ID);
            softly.then(page.content().get(0).target().userId()).isEqualTo(TARGET_ID);
        });
    }

    @Test
    @DisplayName("Bean Validation 을 우회한 페이지 요청 거부")
    void rejectsOutOfRangePageRequest() {
        // Given
        Fixture fixture = fixture();

        // When
        Throwable negativePage = catchThrowable(() -> fixture.service().findRecent(-1, 20));
        Throwable zeroSize = catchThrowable(() -> fixture.service().findRecent(0, 0));
        Throwable oversize = catchThrowable(() -> fixture.service().findRecent(
                0, AccountAuditQueryService.PAGE_SIZE_MAX + 1));

        // Then: 조회 자체를 시작하지 않는다
        thenSoftly(softly -> {
            softly.then(negativePage).isInstanceOf(BusinessException.class);
            softly.then(zeroSize).isInstanceOf(BusinessException.class);
            softly.then(oversize).isInstanceOf(BusinessException.class);
        });
        verifyNoInteractions(fixture.auditRepository());
        verifyNoInteractions(fixture.accountRepository());
    }

    @Test
    @DisplayName("감사가 없으면 계정 조회를 하지 않는다")
    void skipsAccountLookupForEmptyPage() {
        // Given
        Fixture fixture = fixture();
        given(fixture.auditRepository().findRecent(anyInt(), anyInt())).willReturn(
                new AccountPermissionChangeAuditPage(List.of(), 0)
        );

        // When
        AccountPermissionAuditPage page = fixture.service().findRecent(0, 20);

        // Then
        then(page.content()).isEmpty();
        verifyNoInteractions(fixture.accountRepository());
    }

    private static AccountPermissionChangeAudit audit() {
        AccountPermissionChangeAudit audit = mock(AccountPermissionChangeAudit.class);
        given(audit.getAuditType())
                .willReturn(AccountPermissionChangeAuditType.ACCOUNT_ROLE);
        given(audit.getActorUserId()).willReturn(ACTOR_ID);
        given(audit.getTargetUserId()).willReturn(TARGET_ID);
        given(audit.getAction()).willReturn("ROLE_GRANTED");
        given(audit.getBeforeValue()).willReturn("USER");
        given(audit.getAfterValue()).willReturn("SYSTEM_ADMIN");
        given(audit.getReason()).willReturn("운영 인수인계");
        given(audit.getOccurredAt()).willReturn(OCCURRED_AT);
        return audit;
    }

    private static Account account(UUID id, String name) {
        Account account = mock(Account.class);
        given(account.getId()).willReturn(id);
        given(account.getName()).willReturn(name);
        return account;
    }

    private Fixture fixture() {
        AccountPermissionChangeAuditRepository auditRepository =
                mock(AccountPermissionChangeAuditRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        return new Fixture(
                auditRepository,
                accountRepository,
                new AccountAuditQueryService(auditRepository, accountRepository)
        );
    }

    private record Fixture(
            AccountPermissionChangeAuditRepository auditRepository,
            AccountRepository accountRepository,
            AccountAuditQueryService service
    ) {
    }
}
