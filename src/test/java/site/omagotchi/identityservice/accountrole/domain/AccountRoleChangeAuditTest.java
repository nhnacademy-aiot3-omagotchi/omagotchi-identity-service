package site.omagotchi.identityservice.accountrole.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeReason;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class AccountRoleChangeAuditTest {

    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID TARGET_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    @DisplayName("권한 부여 감사 기록의 사유 정규화와 Request ID 예약값")
    void recordsNormalizedGrantAudit() {
        // Given
        AccountStatusChangeReason reason = new AccountStatusChangeReason("  운영 인수인계  ");

        // When
        AccountRoleChangeAudit audit = AccountRoleChangeAudit.record(
                ACTOR_ID,
                TARGET_ID,
                AccountRoleChangeAction.ROLE_GRANTED,
                RecordedGlobalRole.USER,
                RecordedGlobalRole.SYSTEM_ADMIN,
                reason,
                OCCURRED_AT
        );

        // Then
        thenSoftly(softly -> {
            softly.then(audit.getActorUserId()).isEqualTo(ACTOR_ID);
            softly.then(audit.getTargetUserId()).isEqualTo(TARGET_ID);
            softly.then(audit.getBeforeRole()).isEqualTo(RecordedGlobalRole.USER);
            softly.then(audit.getAfterRole()).isEqualTo(RecordedGlobalRole.SYSTEM_ADMIN);
            softly.then(audit.getReason()).isEqualTo("운영 인수인계");
            softly.then(audit.getOccurredAt()).isEqualTo(OCCURRED_AT);
            softly.then(audit.getRequestId()).isNull();
        });
    }

    @Test
    @DisplayName("감사 action과 맞지 않는 역할 전이 거부")
    void rejectsMismatchedAuditTransition() {
        // Given: 부여로 기록하면서 실제로는 회수인 전이
        AccountStatusChangeReason reason = new AccountStatusChangeReason("퇴사 처리");

        // When
        Throwable thrown = catchThrowable(() -> AccountRoleChangeAudit.record(
                ACTOR_ID,
                TARGET_ID,
                AccountRoleChangeAction.ROLE_GRANTED,
                RecordedGlobalRole.SYSTEM_ADMIN,
                RecordedGlobalRole.USER,
                reason,
                OCCURRED_AT
        ));

        // Then: DB CHECK 에 닿기 전에 도메인에서 막는다
        then(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("변화 없는 역할 전이의 감사 기록 거부")
    void rejectsUnchangedRoleTransition() {
        // Given
        AccountStatusChangeReason reason = new AccountStatusChangeReason("확인");

        // When
        Throwable thrown = catchThrowable(() -> AccountRoleChangeAudit.record(
                ACTOR_ID,
                TARGET_ID,
                AccountRoleChangeAction.ROLE_GRANTED,
                RecordedGlobalRole.SYSTEM_ADMIN,
                RecordedGlobalRole.SYSTEM_ADMIN,
                reason,
                OCCURRED_AT
        ));

        // Then
        then(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
