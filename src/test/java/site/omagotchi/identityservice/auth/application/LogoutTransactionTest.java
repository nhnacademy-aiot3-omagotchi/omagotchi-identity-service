package site.omagotchi.identityservice.auth.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import site.omagotchi.identityservice.account.application.AccountAuthenticationService;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LogoutTransactionTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000216"
    );
    private static final UUID FAMILY_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000217"
    );
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";
    private static final String REFRESH_TOKEN_HASH = "a".repeat(64);

    private final AccountAuthenticationService accountAuthenticationService = mock(
            AccountAuthenticationService.class
    );
    private final RefreshTokenHasher refreshTokenHasher = mock(RefreshTokenHasher.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(
            RefreshTokenRepository.class
    );
    private final LogoutTransaction logoutTransaction = new LogoutTransaction(
            accountAuthenticationService,
            refreshTokenHasher,
            refreshTokenRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("Refresh Token 부재 로그아웃의 저장소 접근 생략")
    void skipsRepositoryAccessWhenRefreshTokenIsMissing(String rawRefreshToken) {
        // When
        logoutTransaction.logout(rawRefreshToken);

        // Then
        verifyNoInteractions(
                accountAuthenticationService,
                refreshTokenHasher,
                refreshTokenRepository
        );
    }

    @Test
    @DisplayName("일치하는 Refresh Token이 없는 로그아웃의 멱등 처리")
    void returnsWhenRefreshTokenDoesNotExist() {
        // Given
        given(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).willReturn(REFRESH_TOKEN_HASH);
        given(refreshTokenRepository.findAccountIdByHash(REFRESH_TOKEN_HASH))
                .willReturn(Optional.empty());

        // When
        logoutTransaction.logout(RAW_REFRESH_TOKEN);

        // Then
        verifyNoInteractions(accountAuthenticationService);
        verify(refreshTokenRepository, never()).lockByHash(REFRESH_TOKEN_HASH);
        verify(refreshTokenRepository, never()).revokeFamily(
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("계정 잠금 이후 사라진 Refresh Token의 멱등 처리")
    void returnsWhenRefreshTokenDisappearsAfterAccountLock() {
        // Given
        given(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).willReturn(REFRESH_TOKEN_HASH);
        given(refreshTokenRepository.findAccountIdByHash(REFRESH_TOKEN_HASH))
                .willReturn(Optional.of(ACCOUNT_ID));
        given(refreshTokenRepository.lockByHash(REFRESH_TOKEN_HASH))
                .willReturn(Optional.empty());

        // When
        logoutTransaction.logout(RAW_REFRESH_TOKEN);

        // Then
        InOrder invocationOrder = inOrder(
                accountAuthenticationService,
                refreshTokenRepository
        );
        invocationOrder.verify(refreshTokenRepository)
                .findAccountIdByHash(REFRESH_TOKEN_HASH);
        invocationOrder.verify(accountAuthenticationService)
                .lockAuthenticationById(ACCOUNT_ID);
        invocationOrder.verify(refreshTokenRepository)
                .lockByHash(REFRESH_TOKEN_HASH);
        verify(refreshTokenRepository, never()).revokeFamily(
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("계정과 Refresh Token 잠금 이후 Family 폐기")
    void revokesRefreshTokenFamilyAfterLocks() {
        // Given
        RefreshToken refreshToken = RefreshToken.issue(
                ACCOUNT_ID,
                FAMILY_ID,
                REFRESH_TOKEN_HASH,
                NOW.plusSeconds(60),
                NOW.minusSeconds(60)
        );
        given(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).willReturn(REFRESH_TOKEN_HASH);
        given(refreshTokenRepository.findAccountIdByHash(REFRESH_TOKEN_HASH))
                .willReturn(Optional.of(ACCOUNT_ID));
        given(refreshTokenRepository.lockByHash(REFRESH_TOKEN_HASH))
                .willReturn(Optional.of(refreshToken));

        // When
        logoutTransaction.logout(RAW_REFRESH_TOKEN);

        // Then
        InOrder invocationOrder = inOrder(
                accountAuthenticationService,
                refreshTokenRepository
        );
        invocationOrder.verify(refreshTokenRepository)
                .findAccountIdByHash(REFRESH_TOKEN_HASH);
        invocationOrder.verify(accountAuthenticationService)
                .lockAuthenticationById(ACCOUNT_ID);
        invocationOrder.verify(refreshTokenRepository)
                .lockByHash(REFRESH_TOKEN_HASH);
        invocationOrder.verify(refreshTokenRepository)
                .revokeFamily(
                        FAMILY_ID,
                        NOW,
                        RefreshTokenRevocationReason.LOGOUT
                );
    }
}
