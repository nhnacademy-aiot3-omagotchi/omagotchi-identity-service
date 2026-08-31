package site.omagotchi.identityservice.accountstate.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
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
    @DisplayName("감사 기록 생성 시 사유 정규화와 Request ID 예약값")
    void recordsNormalizedAuditWithoutRequestId() {
        // Given
        AccountStatusChangeReason reason = new AccountStatusChangeReason(
                "  보안 사고 대응  "
        );

        // When
        AccountStatusChangeAudit audit = AccountStatusChangeAudit.record(
                ACTOR_ID,
                TARGET_ID,
                AccountStatusChangeAction.ACCOUNT_DISABLED,
                RecordedAccountStatus.LOCKED,
                RecordedAccountStatus.DISABLED,
                reason,
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
    @DisplayName("감사 action과 맞지 않는 상태 전이 거부")
    void rejectsMismatchedAuditTransition() {
        // Given
        AccountStatusChangeReason reason = new AccountStatusChangeReason("보안 확인");

        // When
        Throwable thrown = catchThrowable(() -> AccountStatusChangeAudit.record(
                ACTOR_ID,
                TARGET_ID,
                AccountStatusChangeAction.ACCOUNT_UNLOCKED,
                RecordedAccountStatus.DISABLED,
                RecordedAccountStatus.ACTIVE,
                reason,
                OCCURRED_AT
        ));

        // Then
        then(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("공백·NUL·최대 길이 초과 상태 변경 사유 거부")
    void rejectsInvalidReason() {
        // Given
        String blank = "  ";
        String unicodeBlank = "\u2003";
        String reasonWithNull = "유효하지 않은\0사유";
        String tooLong = "가".repeat(501);

        // When
        Throwable blankFailure = catchThrowable(() -> new AccountStatusChangeReason(blank));
        Throwable unicodeBlankFailure = catchThrowable(
                () -> new AccountStatusChangeReason(unicodeBlank)
        );
        Throwable nullFailure = catchThrowable(
                () -> new AccountStatusChangeReason(reasonWithNull)
        );
        Throwable tooLongFailure = catchThrowable(
                () -> new AccountStatusChangeReason(tooLong)
        );

        // Then
        then(blankFailure)
                .isInstanceOf(IllegalArgumentException.class);
        then(unicodeBlankFailure)
                .isInstanceOf(IllegalArgumentException.class);
        then(nullFailure)
                .isInstanceOf(IllegalArgumentException.class);
        then(tooLongFailure)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Unicode 공백을 정규화하고 Unicode code point 기준 최대 길이 허용")
    void normalizesUnicodeWhitespaceAndCountsCodePoints() {
        // Given
        String fiveHundredEmoji = "😀".repeat(500);

        // When
        AccountStatusChangeReason reason = new AccountStatusChangeReason(
                "\u2003" + fiveHundredEmoji + "\u2003"
        );

        // Then
        then(reason.value()).isEqualTo(fiveHundredEmoji);
    }
}
