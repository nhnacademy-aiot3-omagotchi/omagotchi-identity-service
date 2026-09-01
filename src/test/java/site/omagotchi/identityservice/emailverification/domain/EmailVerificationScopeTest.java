package site.omagotchi.identityservice.emailverification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class EmailVerificationScopeTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final UUID SCOPE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700901"
    );
    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700902"
    );
    private static final UUID CURRENT_CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700903"
    );
    private static final UUID STALE_CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700904"
    );
    private static final UUID NEXT_CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700905"
    );

    @Test
    @DisplayName("Challenge 시작 시 현재 식별자와 쿨다운을 함께 기록")
    void startsChallengeWithCooldown() {
        // Given
        EmailVerificationScope scope = scope();
        UUID challengeId = CHALLENGE_ID;

        // When
        scope.startChallenge(challengeId, NOW, Duration.ofSeconds(60));

        // Then
        then(scope.getActiveChallengeId()).isEqualTo(challengeId);
        then(scope.canIssueAt(NOW.plusSeconds(59))).isFalse();
        then(scope.retryAfterSecondsAt(NOW.plusMillis(500))).isEqualTo(60);
        then(scope.canIssueAt(NOW.plusSeconds(60))).isTrue();
    }

    @Test
    @DisplayName("현재 Challenge의 전달 실패만 쿨다운 해제")
    void onlyCurrentChallengeReleasesCooldown() {
        // Given
        EmailVerificationScope scope = scope();
        UUID current = CURRENT_CHALLENGE_ID;
        scope.startChallenge(current, NOW, Duration.ofMinutes(1));

        // When
        scope.releaseCooldownForCurrentChallenge(STALE_CHALLENGE_ID, NOW.plusSeconds(1));

        // Then
        then(scope.canIssueAt(NOW.plusSeconds(1))).isFalse();

        // When
        scope.releaseCooldownForCurrentChallenge(current, NOW.plusSeconds(2));

        // Then
        then(scope.canIssueAt(NOW.plusSeconds(2))).isTrue();
    }

    @Test
    @DisplayName("쿨다운 중 새 Challenge 시작 거부")
    void rejectsIssueDuringCooldown() {
        // Given
        EmailVerificationScope scope = scope();
        scope.startChallenge(CURRENT_CHALLENGE_ID, NOW, Duration.ofMinutes(1));

        // When
        // Then
        thenThrownBy(() -> scope.startChallenge(
                NEXT_CHALLENGE_ID,
                NOW.plusSeconds(1),
                Duration.ofMinutes(1)
        )).isInstanceOf(IllegalStateException.class);
    }

    private EmailVerificationScope scope() {
        return EmailVerificationScope.create(
                SCOPE_ID,
                "member@example.com",
                EmailVerificationPurpose.SIGNUP,
                NOW
        );
    }
}
