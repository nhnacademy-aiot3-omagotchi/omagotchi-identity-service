package site.omagotchi.identityservice.accountrole.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

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
    @DisplayName("권한 부여 감사 기록과 Request ID 예약값")
    void recordsGrantAudit() {
        // When
        AccountRoleChangeAudit audit = AccountRoleChangeAudit.create(
                ACTOR_ID,
                TARGET_ID,
                AccountRoleChangeAction.ROLE_GRANTED,
                "운영 인수인계",
                OCCURRED_AT
        );

        // Then
        thenSoftly(softly -> {
            softly.then(audit.getActorUserId()).isEqualTo(ACTOR_ID);
            softly.then(audit.getTargetUserId()).isEqualTo(TARGET_ID);
            softly.then(audit.getReason()).isEqualTo("운영 인수인계");
            softly.then(audit.getOccurredAt()).isEqualTo(OCCURRED_AT);
            softly.then(audit.getRequestId()).isNull();
        });
    }

}
