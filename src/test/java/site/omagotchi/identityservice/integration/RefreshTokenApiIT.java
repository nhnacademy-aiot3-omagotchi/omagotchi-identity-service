package site.omagotchi.identityservice.integration;

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
import site.omagotchi.identityservice.auth.application.session.RefreshTokenHasher;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
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
class RefreshTokenApiIT {

    private static final UUID EXPIRED_TOKEN_FAMILY_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700001"
    );

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
    private AccountStateTestFixture accountStateFixture;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        accountStateFixture = new AccountStateTestFixture(jdbcTemplate);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("로그인 Token Bundle과 Refresh Token Hash 저장")
    void issuesTokenBundleAndStoresOnlyRefreshTokenHash() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");

        // When
        AuthApiTestClient.TokenBundle tokens = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        RefreshToken storedToken = storedToken(tokens.refreshToken());

        // Then
        thenSoftly(softly -> {
            softly.then(tokens.accessToken()).isNotBlank();
            softly.then(tokens.refreshToken()).isNotBlank();
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
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        RefreshToken originalToken = storedToken(login.refreshToken());
        Jwt originalAccessToken = jwtDecoder.decode(login.accessToken());

        // When
        AuthApiTestClient.TokenBundle refreshed = api.refreshSuccessfully(login.refreshToken());
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
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        accountStateFixture.changeStatus(accountId, AccountStatus.LOCKED);

        // When
        AuthApiTestClient.TokenBundle refreshed = api.refreshSuccessfully(login.refreshToken());

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
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        AuthApiTestClient.TokenBundle refreshed = api.refreshSuccessfully(login.refreshToken());
        UUID familyId = storedToken(login.refreshToken()).getFamilyId();
        accountStateFixture.changeStatus(accountId, accountStatus);

        // When
        ResultActions response = api.refresh(refreshed.refreshToken());
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
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        AuthApiTestClient.TokenBundle refreshed = api.refreshSuccessfully(login.refreshToken());
        UUID familyId = storedToken(login.refreshToken()).getFamilyId();

        // When
        ResultActions reusedTokenResponse = api.refresh(login.refreshToken());
        ResultActions activeTokenResponse = api.refresh(refreshed.refreshToken());
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
    @DisplayName("로그아웃 Refresh Family 폐기")
    void revokesRefreshFamilyOnLogout() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );

        // When
        ResultActions logoutResponse = api.logout(login.refreshToken());
        ResultActions refreshAfterLogout = api.refresh(login.refreshToken());
        RefreshToken loggedOutToken = storedToken(login.refreshToken());

        // Then
        logoutResponse.andExpectAll(
                status().isNoContent(),
                header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")),
                header().doesNotExist(HttpHeaders.SET_COOKIE)
        );
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
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        String expiredTokenValue = "expired-refresh-token";
        Instant createdAt = Instant.now().minus(Duration.ofDays(8));
        RefreshToken expiredToken = RefreshToken.issue(
                accountId,
                EXPIRED_TOKEN_FAMILY_ID,
                refreshTokenHasher.hash(expiredTokenValue),
                createdAt.plus(Duration.ofDays(7)),
                createdAt
        );
        refreshTokenJpaRepository.saveAndFlush(expiredToken);

        // When
        ResultActions tamperedTokenResponse = api.refresh(login.refreshToken() + "tampered");
        ResultActions expiredTokenResponse = api.refresh(expiredTokenValue);

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

    private RefreshToken storedToken(String rawRefreshToken) {
        String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
        return refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getTokenHash().equals(tokenHash))
                .findFirst()
                .orElseThrow();
    }

}
