package site.omagotchi.identityservice.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class AccountStatusChangeAuditTest {

    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID TARGET_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    @DisplayName("감사 기록 생성과 Request ID 예약값")
    void recordsAuditWithoutRequestId() {
        // When
        AccountStatusChangeAudit audit = AccountStatusChangeAudit.create(
                ACTOR_ID,
                TARGET_ID,
                AccountStatusChangeAction.ACCOUNT_DISABLED,
                "보안 사고 대응",
                OCCURRED_AT
        );

        // Then
        thenSoftly(softly -> {
            softly.then(audit.getActorUserId()).isEqualTo(ACTOR_ID);
            softly.then(audit.getTargetUserId()).isEqualTo(TARGET_ID);
            softly.then(audit.getReason()).isEqualTo("보안 사고 대응");
            softly.then(audit.getOccurredAt()).isEqualTo(OCCURRED_AT);
            softly.then(audit.getRequestId()).isNull();
        });
    }

    @Test
    @DisplayName("로그인 잠금 해제는 ACTIVE 상태를 유지하는 인증 사건으로 기록")
    void recordsLoginUnlockWithoutLifecycleTransition() {
        AccountStatusChangeAudit audit = AccountStatusChangeAudit.create(
                ACTOR_ID,
                TARGET_ID,
                AccountStatusChangeAction.LOGIN_LOCK_RELEASED,
                "본인 확인 완료",
                OCCURRED_AT
        );

        then(audit.getAction()).isEqualTo(AccountStatusChangeAction.LOGIN_LOCK_RELEASED);
    }

    @Test
    @DisplayName("계정 복구 감사는 WITHDRAWN에서 ACTIVE 전이만 허용")
    void recordsAccountRecovery() {
        AccountStatusChangeAudit audit = AccountStatusChangeAudit.create(
                TARGET_ID,
                TARGET_ID,
                AccountStatusChangeAction.ACCOUNT_RECOVERED,
                "이메일 소유권 확인",
                OCCURRED_AT
        );

        then(audit.getAction()).isEqualTo(AccountStatusChangeAction.ACCOUNT_RECOVERED);
    }

}
