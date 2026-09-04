package site.omagotchi.identityservice.emailverification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;

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
    private static final UUID NEXT_CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700905"
    );

    @Test
    @DisplayName("용도별 Scope에 현재 Challenge 식별자 기록")
    void startsChallenge() {
        // Given
        EmailVerificationScope scope = scope();
        UUID challengeId = CHALLENGE_ID;

        // When
        scope.startChallenge(challengeId, NOW);

        // Then
        then(scope.getActiveChallengeId()).isEqualTo(challengeId);
    }

    @Test
    @DisplayName("새 Challenge가 같은 용도의 현재 Challenge를 대체")
    void replacesCurrentChallenge() {
        // Given
        EmailVerificationScope scope = scope();
        scope.startChallenge(CURRENT_CHALLENGE_ID, NOW);

        // When
        scope.startChallenge(NEXT_CHALLENGE_ID, NOW.plusSeconds(1));

        // Then
        then(scope.getActiveChallengeId()).isEqualTo(NEXT_CHALLENGE_ID);
        then(scope.isCurrentChallenge(CURRENT_CHALLENGE_ID)).isFalse();
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
