package site.omagotchi.identityservice.accountstate.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.account.application.AccountLifecycleService;
import site.omagotchi.identityservice.account.application.result.AccountStateChangeResult;
import site.omagotchi.identityservice.account.application.result.AccountStateValue;
import site.omagotchi.identityservice.accountstate.application.port.AccountStatusChangeAuditRepository;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeAudit;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationReason;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AdminAccountStatusChangeServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000401"
    );
    private static final UUID TARGET_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000402"
    );
    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
    private static final String REASON = "보안 사고 대응";

    @Mock
    private AccountLifecycleService accountLifecycleService;
    @Mock
    private RefreshSessionRevocationService refreshSessionRevocationService;
    @Mock
    private AccountStatusChangeAuditRepository auditRepository;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("계정 비활성화 시 Refresh Token 폐기와 감사 기록 영속")
    void disablesAccountRevokesSessionsAndRecordsAudit() {
        // Given
        AdminAccountStatusChangeService service = new AdminAccountStatusChangeService(
                accountLifecycleService,
                refreshSessionRevocationService,
                auditRepository,
                clock
        );
        AccountStateChangeResult result = new AccountStateChangeResult(
                TARGET_ID,
                AccountStateValue.ACTIVE,
                AccountStateValue.DISABLED
        );
        given(accountLifecycleService.disableByAdministrator(ACTOR_ID, TARGET_ID)).willReturn(result);

        // When
        service.changeStatus(ACTOR_ID, TARGET_ID, AdminAccountStatus.DISABLED, REASON);

        // Then
        verify(accountLifecycleService).disableByAdministrator(ACTOR_ID, TARGET_ID);
        verify(refreshSessionRevocationService).revokeAllForAccount(
                TARGET_ID,
                RefreshSessionRevocationReason.ACCOUNT_DISABLED
        );
        verify(auditRepository).append(any(AccountStatusChangeAudit.class));
    }

    @Test
    @DisplayName("계정 활성화 시 Refresh Token 유지하고 감사 기록 영속")
    void activatesAccountWithoutRevokingSessions() {
        // Given
        AdminAccountStatusChangeService service = new AdminAccountStatusChangeService(
                accountLifecycleService,
                refreshSessionRevocationService,
                auditRepository,
                clock
        );
        AccountStateChangeResult result = new AccountStateChangeResult(
                TARGET_ID,
                AccountStateValue.DISABLED,
                AccountStateValue.ACTIVE
        );
        given(accountLifecycleService.activateByAdministrator(ACTOR_ID, TARGET_ID)).willReturn(result);

        // When
        service.changeStatus(ACTOR_ID, TARGET_ID, AdminAccountStatus.ACTIVE, REASON);

        // Then
        verify(accountLifecycleService).activateByAdministrator(ACTOR_ID, TARGET_ID);
        verifyNoInteractions(refreshSessionRevocationService);
        verify(auditRepository).append(any(AccountStatusChangeAudit.class));
    }

    @Test
    @DisplayName("상태 변경이 없는 동일 상태 요청은 세션 폐기와 감사 기록 생략")
    void skipsRevocationAndAuditWhenStatusUnchanged() {
        // Given
        AdminAccountStatusChangeService service = new AdminAccountStatusChangeService(
                accountLifecycleService,
                refreshSessionRevocationService,
                auditRepository,
                clock
        );
        AccountStateChangeResult result = new AccountStateChangeResult(
                TARGET_ID,
                AccountStateValue.DISABLED,
                AccountStateValue.DISABLED
        );
        given(accountLifecycleService.disableByAdministrator(ACTOR_ID, TARGET_ID)).willReturn(result);

        // When
        service.changeStatus(ACTOR_ID, TARGET_ID, AdminAccountStatus.DISABLED, REASON);

        // Then
        verify(accountLifecycleService).disableByAdministrator(ACTOR_ID, TARGET_ID);
        verifyNoInteractions(refreshSessionRevocationService, auditRepository);
    }

    @Test
    @DisplayName("감사 저장 실패 시 예외 전파 (트랜잭션 롤백 유도)")
    void propagatesExceptionWhenAuditPersistenceFails() {
        // Given
        AdminAccountStatusChangeService service = new AdminAccountStatusChangeService(
                accountLifecycleService,
                refreshSessionRevocationService,
                auditRepository,
                clock
        );
        AccountStateChangeResult result = new AccountStateChangeResult(
                TARGET_ID,
                AccountStateValue.ACTIVE,
                AccountStateValue.DISABLED
        );
        given(accountLifecycleService.disableByAdministrator(ACTOR_ID, TARGET_ID)).willReturn(result);
        willThrow(new IllegalStateException("의도한 감사 저장 실패"))
                .given(auditRepository)
                .append(any(AccountStatusChangeAudit.class));

        // When
        Throwable thrown = catchThrowable(() -> service.changeStatus(
                ACTOR_ID,
                TARGET_ID,
                AdminAccountStatus.DISABLED,
                REASON
        ));

        // Then
        then(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage("의도한 감사 저장 실패");
    }
}
