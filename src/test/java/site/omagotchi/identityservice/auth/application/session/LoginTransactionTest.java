package site.omagotchi.identityservice.auth.application.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.account.application.AccountAuthenticationService;
import site.omagotchi.identityservice.account.application.result.AccountAuthenticationResult;
import site.omagotchi.identityservice.auth.application.port.AccessTokenIssuer;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.application.result.IssuedAccessToken;
import site.omagotchi.identityservice.auth.application.result.IssuedRefreshToken;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
import site.omagotchi.identityservice.auth.domain.RefreshToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginTransactionTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000101"
    );
    private static final UUID FAMILY_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000102"
    );
    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password-passphrase";
    private static final String GLOBAL_ROLE = "USER";

    @Mock
    private AccountAuthenticationService accountAuthenticationService;
    @Mock
    private AccessTokenIssuer accessTokenIssuer;
    @Mock
    private RefreshTokenIssuer refreshTokenIssuer;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("인증 성공 시 Refresh Token 저장과 Access Token 발급")
    void authenticatesAndIssuesTokens() {
        // Given
        LoginTransaction loginTransaction = new LoginTransaction(
                accountAuthenticationService,
                accessTokenIssuer,
                refreshTokenIssuer,
                refreshTokenRepository,
                clock
        );
        AccountAuthenticationResult authResult = new AccountAuthenticationResult(ACCOUNT_ID, GLOBAL_ROLE);
        RefreshToken refreshTokenEntity = RefreshToken.issue(
                ACCOUNT_ID,
                FAMILY_ID,
                "a".repeat(64),
                NOW.plusSeconds(3600),
                NOW
        );
        IssuedRefreshToken issuedRefreshToken = new IssuedRefreshToken("raw-refresh-token", refreshTokenEntity);
        IssuedAccessToken issuedAccessToken = new IssuedAccessToken("raw-access-token", NOW.plusSeconds(300));

        given(accountAuthenticationService.authenticate(EMAIL, PASSWORD))
                .willReturn(Optional.of(authResult));
        given(refreshTokenIssuer.issueNewFamily(ACCOUNT_ID, NOW))
                .willReturn(issuedRefreshToken);
        given(accessTokenIssuer.issue(ACCOUNT_ID, GLOBAL_ROLE))
                .willReturn(issuedAccessToken);

        // When
        Optional<TokenIssueResult> result = loginTransaction.login(EMAIL, PASSWORD);

        // Then
        then(result).isPresent();
        TokenIssueResult tokens = result.get();
        thenSoftly(softly -> {
            softly.then(tokens.userId()).isEqualTo(ACCOUNT_ID);
            softly.then(tokens.globalRole()).isEqualTo(GLOBAL_ROLE);
            softly.then(tokens.accessToken()).isEqualTo("raw-access-token");
            softly.then(tokens.accessTokenExpiresAt()).isEqualTo(NOW.plusSeconds(300));
            softly.then(tokens.refreshToken()).isEqualTo("raw-refresh-token");
            softly.then(tokens.refreshTokenExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
        });

        InOrder order = inOrder(
                accountAuthenticationService,
                refreshTokenIssuer,
                refreshTokenRepository,
                accessTokenIssuer
        );
        order.verify(accountAuthenticationService).authenticate(EMAIL, PASSWORD);
        order.verify(refreshTokenIssuer).issueNewFamily(ACCOUNT_ID, NOW);
        order.verify(refreshTokenRepository).store(refreshTokenEntity);
        order.verify(accessTokenIssuer).issue(ACCOUNT_ID, GLOBAL_ROLE);
    }

    @Test
    @DisplayName("인증 실패 시 Token 발급 생략하고 빈 Optional 반환")
    void returnsEmptyWhenAuthenticationFails() {
        // Given
        LoginTransaction loginTransaction = new LoginTransaction(
                accountAuthenticationService,
                accessTokenIssuer,
                refreshTokenIssuer,
                refreshTokenRepository,
                clock
        );
        given(accountAuthenticationService.authenticate(EMAIL, PASSWORD))
                .willReturn(Optional.empty());

        // When
        Optional<TokenIssueResult> result = loginTransaction.login(EMAIL, PASSWORD);

        // Then
        then(result).isEmpty();
        verifyNoInteractions(refreshTokenIssuer, refreshTokenRepository, accessTokenIssuer);
    }

    @Test
    @DisplayName("Access Token 발급 실패 시 예외 전파 (트랜잭션 롤백 유도)")
    void propagatesExceptionWhenAccessTokenIssuanceFails() {
        // Given
        LoginTransaction loginTransaction = new LoginTransaction(
                accountAuthenticationService,
                accessTokenIssuer,
                refreshTokenIssuer,
                refreshTokenRepository,
                clock
        );
        AccountAuthenticationResult authResult = new AccountAuthenticationResult(ACCOUNT_ID, GLOBAL_ROLE);
        RefreshToken refreshTokenEntity = mock(RefreshToken.class);
        IssuedRefreshToken issuedRefreshToken = new IssuedRefreshToken("raw-refresh-token", refreshTokenEntity);

        given(accountAuthenticationService.authenticate(EMAIL, PASSWORD))
                .willReturn(Optional.of(authResult));
        given(refreshTokenIssuer.issueNewFamily(ACCOUNT_ID, NOW))
                .willReturn(issuedRefreshToken);
        given(accessTokenIssuer.issue(ACCOUNT_ID, GLOBAL_ROLE))
                .willThrow(new IllegalStateException("의도한 Access Token 발급 실패"));

        // When
        Throwable thrown = catchThrowable(() -> loginTransaction.login(EMAIL, PASSWORD));

        // Then
        then(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage("의도한 Access Token 발급 실패");
        verify(refreshTokenRepository).store(refreshTokenEntity);
    }

    @Test
    @DisplayName("Refresh Token 저장 실패 시 예외 전파 및 Access Token 미발급")
    void propagatesExceptionWhenRefreshTokenStorageFails() {
        // Given
        LoginTransaction loginTransaction = new LoginTransaction(
                accountAuthenticationService,
                accessTokenIssuer,
                refreshTokenIssuer,
                refreshTokenRepository,
                clock
        );
        AccountAuthenticationResult authResult = new AccountAuthenticationResult(ACCOUNT_ID, GLOBAL_ROLE);
        RefreshToken refreshTokenEntity = mock(RefreshToken.class);
        IssuedRefreshToken issuedRefreshToken = new IssuedRefreshToken("raw-refresh-token", refreshTokenEntity);

        given(accountAuthenticationService.authenticate(EMAIL, PASSWORD))
                .willReturn(Optional.of(authResult));
        given(refreshTokenIssuer.issueNewFamily(ACCOUNT_ID, NOW))
                .willReturn(issuedRefreshToken);
        given(refreshTokenRepository.store(refreshTokenEntity))
                .willThrow(new IllegalStateException("Refresh Token 저장 실패"));

        // When
        Throwable thrown = catchThrowable(() -> loginTransaction.login(EMAIL, PASSWORD));

        // Then
        then(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage("Refresh Token 저장 실패");
        verify(accessTokenIssuer, never()).issue(ACCOUNT_ID, GLOBAL_ROLE);
    }
}
