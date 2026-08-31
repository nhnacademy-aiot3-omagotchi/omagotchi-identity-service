package site.omagotchi.identityservice.emailverification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class EmailVerificationChallengeTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700801"
    );
    private static final UUID SCOPE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700802"
    );

    @Test
    @DisplayName("새 Challenge는 OPEN·PENDING 상태로 생성")
    void issuesOpenPendingChallenge() {
        // Given
        // Challenge 생성 조건은 fixture에 고정한다.

        // When
        EmailVerificationChallenge challenge = challenge();

        // Then
        then(challenge.getStatus()).isEqualTo(EmailVerificationStatus.OPEN);
        then(challenge.getDeliveryStatus()).isEqualTo(EmailDeliveryStatus.PENDING);
        then(challenge.getFailedAttempts()).isZero();
        then(challenge.isUsableAt(NOW.plusSeconds(299))).isTrue();
        then(challenge.isUsableAt(NOW.plusSeconds(300))).isFalse();
    }

    @Test
    @DisplayName("최대 실패 횟수에서 Challenge 소진")
    void exhaustsAtMaximumFailedAttempts() {
        // Given
        EmailVerificationChallenge challenge = challenge();

        // When
        challenge.recordInvalidAttempt(2, NOW.plusSeconds(1));
        challenge.recordInvalidAttempt(2, NOW.plusSeconds(2));

        // Then
        then(challenge.getFailedAttempts()).isEqualTo((short) 2);
        then(challenge.getStatus()).isEqualTo(EmailVerificationStatus.EXHAUSTED);
        then(challenge.isUsableAt(NOW.plusSeconds(3))).isFalse();
    }

    @Test
    @DisplayName("소비한 Challenge 재사용 거부")
    void consumesOnlyOnce() {
        // Given
        EmailVerificationChallenge challenge = challenge();

        // When
        challenge.consume(NOW.plusSeconds(1));

        // Then
        then(challenge.getStatus()).isEqualTo(EmailVerificationStatus.CONSUMED);
        thenThrownBy(() -> challenge.consume(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("전달 상태와 인증 상태를 독립적으로 전이")
    void changesDeliveryAndVerificationIndependently() {
        // Given
        EmailVerificationChallenge challenge = challenge();

        // When
        challenge.markDeliveryFailed(NOW.plusSeconds(1));
        challenge.supersede(NOW.plusSeconds(2));

        // Then
        then(challenge.getDeliveryStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        then(challenge.getStatus()).isEqualTo(EmailVerificationStatus.SUPERSEDED);
    }

    private EmailVerificationChallenge challenge() {
        return EmailVerificationChallenge.issue(
                CHALLENGE_ID,
                SCOPE_ID,
                "member@example.com",
                EmailVerificationPurpose.SIGNUP,
                "a".repeat(64),
                NOW.plusSeconds(300),
                NOW
        );
    }
}
