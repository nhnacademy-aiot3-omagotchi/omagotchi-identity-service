package site.omagotchi.identityservice;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.RefreshTokenHasher;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import site.omagotchi.identityservice.auth.presentation.RefreshTokenCookieFactory;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class RefreshTokenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    private AuthApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("로그인 Refresh Cookie와 Hash 저장")
    void issuesRefreshCookieAndStoresOnlyHash() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");

        // When
        AuthApiTestClient.LoginTokens tokens = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        RefreshToken storedToken = storedToken(tokens.refreshToken());

        // Then
        thenSoftly(softly -> {
            softly.then(tokens.accessToken()).isNotBlank();
            softly.then(tokens.refreshCookie().isHttpOnly()).isTrue();
            softly.then(tokens.refreshCookie().getSecure()).isFalse();
            softly.then(tokens.refreshCookie().getPath()).isEqualTo("/api/v1/auth");
            softly.then(tokens.refreshCookie().getAttribute("SameSite")).isEqualTo("Strict");
            softly.then(tokens.refreshCookie().getMaxAge()).isBetween(604_799, 604_800);
            softly.then(storedToken.getTokenHash())
                    .isEqualTo(refreshTokenHasher.hash(tokens.refreshToken()))
                    .isNotEqualTo(tokens.refreshToken());
            softly.then(Duration.between(storedToken.getCreatedAt(), storedToken.getExpiresAt()))
                    .isEqualTo(Duration.ofDays(7));
        });
    }

    @Test
    @DisplayName("Refresh Token 회전")
    void rotatesRefreshTokenWithoutExtendingFamilyExpiration() throws Exception {
        // Given
        UUID userId = api.signupSuccessfully("user@example.com");
        AuthApiTestClient.LoginTokens login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        RefreshToken originalToken = storedToken(login.refreshToken());
        Jwt originalAccessToken = jwtDecoder.decode(login.accessToken());

        // When
        AuthApiTestClient.LoginTokens refreshed = api.refreshSuccessfully(login.refreshCookie());
        RefreshToken usedToken = storedToken(login.refreshToken());
        RefreshToken nextToken = storedToken(refreshed.refreshToken());
        Jwt refreshedAccessToken = jwtDecoder.decode(refreshed.accessToken());

        // Then
        thenSoftly(softly -> {
            softly.then(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
            softly.then(usedToken.isUsed()).isTrue();
            softly.then(nextToken.getFamilyId()).isEqualTo(originalToken.getFamilyId());
            softly.then(nextToken.getExpiresAt()).isEqualTo(originalToken.getExpiresAt());
            softly.then(refreshedAccessToken.getSubject()).isEqualTo(userId.toString());
            softly.then(refreshedAccessToken.getId()).isNotEqualTo(originalAccessToken.getId());
        });
    }

    @Test
    @DisplayName("잠긴 계정의 기존 Refresh Token 갱신 허용")
    void allowsRefreshForLockedAccount() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        AuthApiTestClient.LoginTokens login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        changeAccountStatus(accountId, AccountStatus.LOCKED);

        // When
        AuthApiTestClient.LoginTokens refreshed = api.refreshSuccessfully(login.refreshCookie());

        // Then
        then(jwtDecoder.decode(refreshed.accessToken()).getSubject())
                .isEqualTo(accountId.toString());
    }

    @ParameterizedTest
    @CsvSource({
            "DISABLED, ACCOUNT_DISABLED",
            "WITHDRAWN, ACCOUNT_WITHDRAWN"
    })
    @DisplayName("Refresh 불가 계정의 Token Family 폐기")
    void revokesFamilyForAccountWithoutRefreshAccess(
            AccountStatus accountStatus,
            RefreshTokenRevocationReason expectedRevocationReason
    ) throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        AuthApiTestClient.LoginTokens login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        AuthApiTestClient.LoginTokens refreshed = api.refreshSuccessfully(login.refreshCookie());
        UUID familyId = storedToken(login.refreshToken()).getFamilyId();
        changeAccountStatus(accountId, accountStatus);

        // When
        ResultActions response = api.refresh(refreshed.refreshCookie());
        List<RefreshToken> family = refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getFamilyId().equals(familyId))
                .toList();

        // Then
        response.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        then(family)
                .hasSize(2)
                .allSatisfy(token -> thenSoftly(softly -> {
                    softly.then(token.isRevoked()).isTrue();
                    softly.then(token.getRevocationReason())
                            .isEqualTo(expectedRevocationReason);
                }));
    }

    @Test
    @DisplayName("사용한 Refresh Token 재사용 시 Family 폐기")
    void revokesFamilyWhenUsedRefreshTokenIsReused() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.LoginTokens login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        AuthApiTestClient.LoginTokens refreshed = api.refreshSuccessfully(login.refreshCookie());
        UUID familyId = storedToken(login.refreshToken()).getFamilyId();

        // When
        ResultActions reusedTokenResponse = api.refresh(login.refreshCookie());
        ResultActions activeTokenResponse = api.refresh(refreshed.refreshCookie());
        List<RefreshToken> family = refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getFamilyId().equals(familyId))
                .toList();

        // Then
        reusedTokenResponse.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        activeTokenResponse.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        then(family)
                .allSatisfy(token -> thenSoftly(softly -> {
                    softly.then(token.isRevoked()).isTrue();
                    softly.then(token.getRevocationReason())
                            .isEqualTo(RefreshTokenRevocationReason.REUSE_DETECTED);
                }));
    }

    @Test
    @DisplayName("로그아웃 Refresh Family 폐기와 Cookie 만료")
    void revokesRefreshFamilyAndExpiresCookieOnLogout() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.LoginTokens login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );

        // When
        ResultActions logoutResponse = api.logout(login.refreshCookie());
        ResultActions refreshAfterLogout = api.refresh(login.refreshCookie());
        RefreshToken loggedOutToken = storedToken(login.refreshToken());

        // Then
        logoutResponse.andExpect(status().isNoContent())
                .andExpect(result -> {
                    Cookie expiredCookie = result.getResponse()
                            .getCookie(RefreshTokenCookieFactory.COOKIE_NAME);
                    then(expiredCookie).isNotNull();
                    then(expiredCookie.getMaxAge()).isZero();
                });
        refreshAfterLogout.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        thenSoftly(softly -> {
            softly.then(loggedOutToken.isRevoked()).isTrue();
            softly.then(loggedOutToken.getRevocationReason())
                    .isEqualTo(RefreshTokenRevocationReason.LOGOUT);
        });
    }

    @Test
    @DisplayName("변조·만료 Refresh Token 거부")
    void rejectsTamperedAndExpiredRefreshTokens() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        AuthApiTestClient.LoginTokens login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        String expiredTokenValue = "expired-refresh-token";
        Instant createdAt = Instant.now().minus(Duration.ofDays(8));
        RefreshToken expiredToken = RefreshToken.issue(
                accountId,
                UUID.randomUUID(),
                refreshTokenHasher.hash(expiredTokenValue),
                createdAt.plus(Duration.ofDays(7)),
                createdAt
        );
        refreshTokenJpaRepository.saveAndFlush(expiredToken);

        // When
        ResultActions tamperedTokenResponse = api.refresh(new Cookie(
                RefreshTokenCookieFactory.COOKIE_NAME,
                login.refreshToken() + "tampered"
        ));
        ResultActions expiredTokenResponse = api.refresh(new Cookie(
                RefreshTokenCookieFactory.COOKIE_NAME,
                expiredTokenValue
        ));

        // Then
        tamperedTokenResponse.andExpectAll(
                status().isUnauthorized(),
                header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        expiredTokenResponse.andExpectAll(
                status().isUnauthorized(),
                header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
    }

    @Test
    @DisplayName("허용되지 않은 Origin의 Refresh·로그아웃 거부")
    void rejectsRefreshAndLogoutFromUntrustedOrigin() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.LoginTokens login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        String untrustedOrigin = "https://attacker.example";

        // When
        ResultActions refreshResponse = api.refresh(login.refreshCookie(), untrustedOrigin);
        ResultActions logoutResponse = api.logout(login.refreshCookie(), untrustedOrigin);
        ResultActions allowedRefreshResponse = api.refresh(login.refreshCookie());

        // Then
        refreshResponse.andExpectAll(
                status().isForbidden(),
                jsonPath("$.code").value("AUTH_INVALID_REQUEST_ORIGIN")
        );
        logoutResponse.andExpectAll(
                status().isForbidden(),
                jsonPath("$.code").value("AUTH_INVALID_REQUEST_ORIGIN")
        );
        allowedRefreshResponse.andExpect(status().isOk());
    }

    private RefreshToken storedToken(String rawRefreshToken) {
        String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
        return refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getTokenHash().equals(tokenHash))
                .findFirst()
                .orElseThrow();
    }

    private void changeAccountStatus(UUID accountId, AccountStatus accountStatus) {
        jdbcTemplate.update(
                """
                        UPDATE identity_service.accounts
                        SET status = ?,
                            locked_until = CASE
                                WHEN ? = 'LOCKED' THEN CURRENT_TIMESTAMP + INTERVAL '1 hour'
                                ELSE NULL
                            END,
                            withdrawn_at = CASE
                                WHEN ? = 'WITHDRAWN' THEN CURRENT_TIMESTAMP
                                ELSE NULL
                            END
                        WHERE id = ?
                        """,
                accountStatus.name(),
                accountStatus.name(),
                accountStatus.name(),
                accountId
        );
    }

}
