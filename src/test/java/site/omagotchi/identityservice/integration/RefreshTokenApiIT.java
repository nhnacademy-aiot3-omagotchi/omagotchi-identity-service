package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationReason;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationService;
import site.omagotchi.identityservice.auth.application.session.RefreshTokenHasher;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RefreshTokenApiIT extends BaseIntegrationTest {

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
    private RefreshSessionRevocationService refreshSessionRevocationService;

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
        cleanDatabase();
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

    @Test
    @DisplayName("사용자의 모든 Refresh Token Family 폐기와 다른 사용자 격리")
    void revokesAllFamiliesForOnlyTargetAccount() throws Exception {
        // Given
        UUID targetAccountId = api.signupSuccessfully("target@example.com");
        AuthApiTestClient.TokenBundle firstFamily = api.loginSuccessfully(
                "target@example.com",
                "password-passphrase"
        );
        AuthApiTestClient.TokenBundle rotatedFirstFamily = api.refreshSuccessfully(
                firstFamily.refreshToken()
        );
        AuthApiTestClient.TokenBundle secondFamily = api.loginSuccessfully(
                "target@example.com",
                "password-passphrase"
        );

        UUID otherAccountId = api.signupSuccessfully("other@example.com");
        AuthApiTestClient.TokenBundle otherFamily = api.loginSuccessfully(
                "other@example.com",
                "password-passphrase"
        );

        // When
        refreshSessionRevocationService.revokeAllForAccount(
                targetAccountId,
                RefreshSessionRevocationReason.PASSWORD_CHANGED
        );
        Map<Long, RevocationState> firstRevocationStates = revocationStates(
                tokensFor(targetAccountId)
        );

        // 다른 사유의 반복 호출에도 최초 폐기 상태를 보존하는 멱등 경계
        refreshSessionRevocationService.revokeAllForAccount(
                targetAccountId,
                RefreshSessionRevocationReason.ACCOUNT_DISABLED
        );

        List<RefreshToken> targetTokens = tokensFor(targetAccountId);
        List<RefreshToken> otherTokens = tokensFor(otherAccountId);

        // Then
        api.refresh(rotatedFirstFamily.refreshToken()).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        api.refresh(secondFamily.refreshToken()).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        AuthApiTestClient.TokenBundle refreshedOther = api.refreshSuccessfully(
                otherFamily.refreshToken()
        );

        thenSoftly(softly -> {
            softly.then(targetTokens).hasSize(3);
            softly.then(targetTokens).allSatisfy(token -> {
                RevocationState firstState = firstRevocationStates.get(token.getId());

                softly.then(firstState).isNotNull();
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevokedAt()).isEqualTo(firstState.revokedAt());
                softly.then(token.getRevocationReason())
                        .isEqualTo(firstState.reason())
                        .isEqualTo(RefreshTokenRevocationReason.PASSWORD_CHANGED);
            });
            softly.then(otherTokens).hasSize(1);
            softly.then(otherTokens.getFirst().isRevoked()).isFalse();
            softly.then(refreshedOther.userId()).isEqualTo(otherAccountId);
        });
    }

    private List<RefreshToken> tokensFor(UUID accountId) {
        return refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getAccountId().equals(accountId))
                .toList();
    }

    private Map<Long, RevocationState> revocationStates(List<RefreshToken> tokens) {
        return tokens.stream().collect(Collectors.toMap(
                RefreshToken::getId,
                token -> new RevocationState(
                        token.getRevokedAt(),
                        token.getRevocationReason()
                )
        ));
    }

    private record RevocationState(
            Instant revokedAt,
            RefreshTokenRevocationReason reason
    ) {
    }
}
