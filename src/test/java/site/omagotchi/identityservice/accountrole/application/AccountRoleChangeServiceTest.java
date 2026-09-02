package site.omagotchi.identityservice.accountrole.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import site.omagotchi.identityservice.account.application.AccountLifecycleService;
import site.omagotchi.identityservice.account.application.result.AccountRoleChangeResult;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.accountrole.application.port.AccountRoleChangeAuditRepository;
import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAction;
import site.omagotchi.identityservice.accountrole.domain.AccountRoleChangeAudit;
import site.omagotchi.identityservice.accountrole.domain.RecordedGlobalRole;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AccountRoleChangeServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID TARGET_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final Instant NOW = Instant.parse("2026-09-01T03:00:00Z");

    @Test
    @DisplayName("권한 부여의 감사 기록 저장")
    void appendsAuditOnRoleGrant() {
        // Given
        Fixture fixture = fixture();
        given(fixture.lifecycleService().changeGlobalRoleByAdministrator(
                ACTOR_ID, TARGET_ID, GlobalRole.SYSTEM_ADMIN
        )).willReturn(new AccountRoleChangeResult(
                TARGET_ID, GlobalRole.USER, GlobalRole.SYSTEM_ADMIN
        ));

        // When
        fixture.service().changeGlobalRole(
                ACTOR_ID, TARGET_ID, AdminGlobalRole.SYSTEM_ADMIN, "  운영 인수인계  "
        );

        // Then
        ArgumentCaptor<AccountRoleChangeAudit> captor =
                ArgumentCaptor.forClass(AccountRoleChangeAudit.class);
        verify(fixture.auditRepository()).append(captor.capture());
        AccountRoleChangeAudit audit = captor.getValue();
        thenSoftly(softly -> {
            softly.then(audit.getActorUserId()).isEqualTo(ACTOR_ID);
            softly.then(audit.getTargetUserId()).isEqualTo(TARGET_ID);
            softly.then(audit.getAction()).isEqualTo(AccountRoleChangeAction.ROLE_GRANTED);
            softly.then(audit.getBeforeRole()).isEqualTo(RecordedGlobalRole.USER);
            softly.then(audit.getAfterRole()).isEqualTo(RecordedGlobalRole.SYSTEM_ADMIN);
            // 사유는 값 객체가 정규화한 뒤 저장된다
            softly.then(audit.getReason()).isEqualTo("운영 인수인계");
            softly.then(audit.getOccurredAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("같은 역할 요청의 감사 기록 생략")
    void skipsAuditWhenRoleUnchanged() {
        // Given: 이미 SYSTEM_ADMIN 인 계정에 같은 역할을 다시 요청
        Fixture fixture = fixture();
        given(fixture.lifecycleService().changeGlobalRoleByAdministrator(
                ACTOR_ID, TARGET_ID, GlobalRole.SYSTEM_ADMIN
        )).willReturn(new AccountRoleChangeResult(
                TARGET_ID, GlobalRole.SYSTEM_ADMIN, GlobalRole.SYSTEM_ADMIN
        ));

        // When
        fixture.service().changeGlobalRole(
                ACTOR_ID, TARGET_ID, AdminGlobalRole.SYSTEM_ADMIN, "재확인"
        );

        // Then: 아무것도 바뀌지 않았으므로 감사 기록도 남기지 않는다
        verify(fixture.auditRepository(), never()).append(any());
    }

    @Test
    @DisplayName("계정 행 잠금 전 사유 검증")
    void validatesReasonBeforeLocking() {
        // Given
        Fixture fixture = fixture();

        // When
        Throwable thrown = catchThrowable(() -> fixture.service().changeGlobalRole(
                ACTOR_ID, TARGET_ID, AdminGlobalRole.USER, "   "
        ));

        // Then: 잠금을 잡기 전에 끊어 불필요한 직렬화를 만들지 않는다
        then(thrown).isInstanceOf(BusinessException.class);
        verifyNoInteractions(fixture.lifecycleService());
        verifyNoInteractions(fixture.auditRepository());
    }

    private Fixture fixture() {
        AccountLifecycleService lifecycleService = mock(AccountLifecycleService.class);
        AccountRoleChangeAuditRepository auditRepository =
                mock(AccountRoleChangeAuditRepository.class);
        return new Fixture(
                lifecycleService,
                auditRepository,
                new AccountRoleChangeService(
                        lifecycleService,
                        auditRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                )
        );
    }

    private record Fixture(
            AccountLifecycleService lifecycleService,
            AccountRoleChangeAuditRepository auditRepository,
            AccountRoleChangeService service
    ) {
    }
}
