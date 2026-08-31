package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class PasswordChangeIT {

    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("비밀번호 변경 후 모든 Refresh Session 폐기와 새 비밀번호 로그인")
    void changesPasswordAndRevokesEveryRefreshSession() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle firstLogin = api.loginSuccessfully(
                "user@example.com",
                CURRENT_PASSWORD
        );
        AuthApiTestClient.TokenBundle secondLogin = api.loginSuccessfully(
                "user@example.com",
                CURRENT_PASSWORD
        );
        UUID otherAccountId = api.signupSuccessfully("other@example.com");
        AuthApiTestClient.TokenBundle otherLogin = api.loginSuccessfully(
                "other@example.com",
                CURRENT_PASSWORD
        );

        // When
        ResultActions response = api.changePassword(
                firstLogin.accessToken(),
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );
        Account changedAccount = accountJpaRepository.findById(accountId).orElseThrow();
        List<RefreshToken> revokedTokens = tokensFor(accountId);

        // Then
        response.andExpectAll(
                status().isNoContent(),
                content().string(""),
                header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store"))
        );
        thenSoftly(softly -> {
            softly.then(passwordHasher.matches(
                    NEW_PASSWORD,
                    changedAccount.getPasswordHash()
            )).isTrue();
            softly.then(passwordHasher.matches(
                    CURRENT_PASSWORD,
                    changedAccount.getPasswordHash()
            )).isFalse();
            softly.then(revokedTokens).hasSize(2).allSatisfy(token -> {
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevocationReason())
                        .isEqualTo(RefreshTokenRevocationReason.PASSWORD_CHANGED);
            });
        });

        // 이미 발급된 Access JWT는 denylist 없이 만료까지 유효
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstLogin.accessToken()))
                .andExpect(status().isOk());
        api.login("user@example.com", CURRENT_PASSWORD).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS")
        );
        api.refresh(firstLogin.refreshToken()).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        api.refresh(secondLogin.refreshToken()).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        api.loginSuccessfully("user@example.com", NEW_PASSWORD);
        AuthApiTestClient.TokenBundle refreshedOther = api.refreshSuccessfully(
                otherLogin.refreshToken()
        );
        thenSoftly(softly -> {
            softly.then(refreshedOther.userId()).isEqualTo(otherAccountId);
            softly.then(tokensFor(otherAccountId)).allSatisfy(token ->
                    softly.then(token.isRevoked()).isFalse()
            );
        });
    }

    @Test
    @DisplayName("현재 비밀번호 불일치 시 Hash와 Session 유지")
    void preservesPasswordAndSessionsWhenCurrentPasswordMismatches() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                CURRENT_PASSWORD
        );
        String originalHash = accountJpaRepository.findById(accountId)
                .orElseThrow()
                .getPasswordHash();

        // When
        ResultActions response = api.changePassword(
                login.accessToken(),
                "wrong-password-passphrase",
                NEW_PASSWORD
        );
        Account unchangedAccount = accountJpaRepository.findById(accountId).orElseThrow();
        List<RefreshToken> tokens = tokensFor(accountId);

        // Then
        response.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("ACCOUNT_CURRENT_PASSWORD_MISMATCH")
        );
        thenSoftly(softly -> {
            softly.then(unchangedAccount.getPasswordHash()).isEqualTo(originalHash);
            softly.then(tokens).hasSize(1);
            softly.then(tokens.getFirst().isRevoked()).isFalse();
        });
        api.refreshSuccessfully(login.refreshToken());
    }

    @Test
    @DisplayName("새 비밀번호 정책 위반과 현재 비밀번호 재사용 거부")
    void rejectsInvalidAndUnchangedNewPasswords() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                CURRENT_PASSWORD
        );

        // When
        ResultActions invalidPassword = api.changePassword(
                login.accessToken(),
                CURRENT_PASSWORD,
                "too-short"
        );
        ResultActions unchangedPassword = api.changePassword(
                login.accessToken(),
                CURRENT_PASSWORD,
                CURRENT_PASSWORD
        );

        // Then
        invalidPassword.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("ACCOUNT_INVALID_PASSWORD")
        );
        unchangedPassword.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("ACCOUNT_PASSWORD_UNCHANGED")
        );
        Account account = accountJpaRepository.findById(accountId).orElseThrow();
        thenSoftly(softly -> {
            softly.then(passwordHasher.matches(
                    CURRENT_PASSWORD,
                    account.getPasswordHash()
            )).isTrue();
            softly.then(tokensFor(accountId)).hasSize(1).allSatisfy(token ->
                    softly.then(token.isRevoked()).isFalse()
            );
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountStatus.class, names = {"DISABLED", "WITHDRAWN"})
    @DisplayName("비활성 계정의 비밀번호 변경 거부")
    void rejectsPasswordChangeForUnavailableAccount(AccountStatus accountStatus) throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                CURRENT_PASSWORD
        );
        String originalHash = accountJpaRepository.findById(accountId)
                .orElseThrow()
                .getPasswordHash();
        accountStateFixture.changeStatus(accountId, accountStatus);

        // When
        ResultActions response = api.changePassword(
                login.accessToken(),
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );

        // Then
        response.andExpectAll(
                status().isForbidden(),
                jsonPath("$.code").value("ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED")
        );
        Account account = accountJpaRepository.findById(accountId).orElseThrow();
        thenSoftly(softly -> {
            softly.then(account.getPasswordHash()).isEqualTo(originalHash);
            softly.then(tokensFor(accountId)).hasSize(1).allSatisfy(token ->
                    softly.then(token.isRevoked()).isFalse()
            );
        });
    }

    @Test
    @DisplayName("로그인 잠금은 기존 인증 사용자의 비밀번호 변경을 막지 않음")
    void allowsPasswordChangeForLoginLockedAccount() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                CURRENT_PASSWORD
        );
        accountStateFixture.changeStatus(accountId, AccountStatus.LOCKED);

        // When
        ResultActions response = api.changePassword(
                login.accessToken(),
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );

        // Then
        response.andExpect(status().isNoContent());
        Account account = accountJpaRepository.findById(accountId).orElseThrow();
        thenSoftly(softly -> {
            softly.then(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
            softly.then(passwordHasher.matches(
                    NEW_PASSWORD,
                    account.getPasswordHash()
            )).isTrue();
            softly.then(tokensFor(accountId)).hasSize(1).allSatisfy(token -> {
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevocationReason())
                        .isEqualTo(RefreshTokenRevocationReason.PASSWORD_CHANGED);
            });
        });
    }

    @Test
    @DisplayName("Bearer Token 없는 비밀번호 변경 요청 거부")
    void requiresBearerAuthentication() throws Exception {
        // Given
        String requestBody = """
                {
                  "currentPassword": "password-passphrase",
                  "newPassword": "new-password-passphrase"
                }
                """;

        // When
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType("application/json")
                        .content(requestBody))
                // Then
                .andExpectAll(
                        status().isUnauthorized(),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                );
    }

    private List<RefreshToken> tokensFor(UUID accountId) {
        return refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getAccountId().equals(accountId))
                .toList();
    }
}
