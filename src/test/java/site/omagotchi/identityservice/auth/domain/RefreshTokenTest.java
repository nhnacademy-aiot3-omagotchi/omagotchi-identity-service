package site.omagotchi.identityservice.auth.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

@DisplayName("Refresh Token")
class RefreshTokenTest {

    private static final String TOKEN_HASH = "a".repeat(64);
    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700401"
    );
    private static final UUID TOKEN_FAMILY_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700402"
    );

    @Test
    @DisplayName("발급 후 한 번만 사용")
    void marksIssuedTokenUsedOnlyOnce() {
        // Given
        Instant issuedAt = Instant.parse("2026-07-24T00:00:00Z");
        RefreshToken refreshToken = issue(issuedAt);

        // When
        refreshToken.markUsed(issuedAt.plus(1, ChronoUnit.HOURS));
        Throwable secondUse = catchThrowable(() ->
                refreshToken.markUsed(issuedAt.plus(2, ChronoUnit.HOURS))
        );

        // Then
        thenSoftly(softly -> {
            softly.then(refreshToken.isUsed()).isTrue();
            softly.then(secondUse).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    @DisplayName("만료 시각부터 사용 불가")
    void rejectsTokenAtExpiration() {
        // Given
        Instant issuedAt = Instant.parse("2026-07-24T00:00:00Z");
        RefreshToken refreshToken = issue(issuedAt);

        // When
        Throwable thrown = catchThrowable(() ->
                refreshToken.markUsed(issuedAt.plus(7, ChronoUnit.DAYS))
        );

        // Then
        thenSoftly(softly -> {
            softly.then(refreshToken.isExpiredAt(issuedAt.plus(7, ChronoUnit.DAYS))).isTrue();
            softly.then(thrown).isInstanceOf(IllegalStateException.class);
        });
    }

    private RefreshToken issue(Instant issuedAt) {
        return RefreshToken.issue(
                ACCOUNT_ID,
                TOKEN_FAMILY_ID,
                TOKEN_HASH,
                issuedAt.plus(7, ChronoUnit.DAYS),
                issuedAt
        );
    }
}
