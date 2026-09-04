package site.omagotchi.identityservice.emailverification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class EmailDeliveryCooldownTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
    private static final UUID CURRENT_CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000702001"
    );
    private static final UUID STALE_CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000702002"
    );

    @Test
    @DisplayName("이메일 전체 발송 예약과 남은 쿨다운 계산")
    void reservesEmailDelivery() {
        // Given
        EmailDeliveryCooldown cooldown = cooldown();

        // When
        cooldown.reserve(CURRENT_CHALLENGE_ID, NOW, Duration.ofMinutes(1));

        // Then
        then(cooldown.getActiveChallengeId()).isEqualTo(CURRENT_CHALLENGE_ID);
        then(cooldown.canIssueAt(NOW.plusSeconds(59))).isFalse();
        then(cooldown.retryAfterSecondsAt(NOW.plusMillis(500))).isEqualTo(60);
        then(cooldown.canIssueAt(NOW.plusSeconds(60))).isTrue();
    }

    @Test
    @DisplayName("현재 발송 예약 소유자만 공유 쿨다운 해제")
    void onlyReservationOwnerReleasesCooldown() {
        // Given
        EmailDeliveryCooldown cooldown = cooldown();
        cooldown.reserve(CURRENT_CHALLENGE_ID, NOW, Duration.ofMinutes(1));

        // When
        cooldown.releaseIfReservedBy(STALE_CHALLENGE_ID, NOW.plusSeconds(1));

        // Then
        then(cooldown.canIssueAt(NOW.plusSeconds(1))).isFalse();

        // When
        cooldown.releaseIfReservedBy(CURRENT_CHALLENGE_ID, NOW.plusSeconds(2));

        // Then
        then(cooldown.canIssueAt(NOW.plusSeconds(2))).isTrue();
    }

    @Test
    @DisplayName("공유 쿨다운 중 다른 용도의 발송 예약도 거부")
    void rejectsReservationDuringCooldown() {
        // Given
        EmailDeliveryCooldown cooldown = cooldown();
        cooldown.reserve(CURRENT_CHALLENGE_ID, NOW, Duration.ofMinutes(1));

        // When
        // Then
        thenThrownBy(() -> cooldown.reserve(
                STALE_CHALLENGE_ID,
                NOW.plusSeconds(1),
                Duration.ofMinutes(1)
        )).isInstanceOf(IllegalStateException.class);
    }

    private EmailDeliveryCooldown cooldown() {
        return EmailDeliveryCooldown.create("member@example.com", NOW);
    }
}
