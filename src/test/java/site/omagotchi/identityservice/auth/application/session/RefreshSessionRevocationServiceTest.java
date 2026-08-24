package site.omagotchi.identityservice.auth.application.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import site.omagotchi.identityservice.account.application.AccountAuthenticationService;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class RefreshSessionRevocationServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000216"
    );
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    @ParameterizedTest(name = "{0}")
    @EnumSource(RefreshSessionRevocationReason.class)
    @DisplayName("계정 전체 Session 폐기 사유를 Token 폐기 사유로 변환")
    void mapsEverySessionRevocationReason(
            RefreshSessionRevocationReason sessionReason
    ) {
        // Given
        AccountAuthenticationService accountAuthenticationService = mock(
                AccountAuthenticationService.class
        );
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        RefreshSessionRevocationService service = new RefreshSessionRevocationService(
                accountAuthenticationService,
                refreshTokenRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        // When
        service.revokeAllForAccount(ACCOUNT_ID, sessionReason);

        // Then
        InOrder invocationOrder = inOrder(
                accountAuthenticationService,
                refreshTokenRepository
        );
        invocationOrder.verify(accountAuthenticationService)
                .lockAuthenticationById(ACCOUNT_ID);
        invocationOrder.verify(refreshTokenRepository)
                .revokeAllByAccountId(
                        ACCOUNT_ID,
                        NOW,
                        RefreshTokenRevocationReason.valueOf(sessionReason.name())
                );
    }
}
