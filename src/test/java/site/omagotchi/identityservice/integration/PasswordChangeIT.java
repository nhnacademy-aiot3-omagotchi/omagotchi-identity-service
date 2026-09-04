package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.PasswordChangeService;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;
import site.omagotchi.identityservice.global.exception.BusinessException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.BDDAssertions.*;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PasswordChangeIT extends BaseIntegrationTest {

    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";
    private static final String FIRST_NEW_PASSWORD = "first-new-password-passphrase";
    private static final String SECOND_NEW_PASSWORD = "second-new-password-passphrase";
    private static final Duration LOCK_CHECK_TIMEOUT = Duration.ofMillis(500);
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

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
    private PasswordChangeService passwordChangeService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailVerificationMailSender mailSender;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private AuthApiTestClient api;
    private AccountStateTestFixture accountStateFixture;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        accountStateFixture = new AccountStateTestFixture(jdbcTemplate);
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
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
    @DisplayName("v2 OTP로 비밀번호 변경 후 Session 폐기와 Challenge 소비")
    void changesPasswordWithEmailOtp() throws Exception {
        // Given
        String email = "password-v2@example.com";
        UUID accountId = api.signupSuccessfully(email);
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(email, CURRENT_PASSWORD);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        String issueResponse = mockMvc.perform(post("/api/v2/users/me/password/email-otp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpectAll(
                        status().isAccepted(),
                        header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store"))
                )
                .andReturn().getResponse().getContentAsString();
        UUID challengeId = UUID.fromString(
                objectMapper.readTree(issueResponse).get("challengeId").asString()
        );
        verify(mailSender).sendVerificationCode(
                eq(challengeId),
                eq(email),
                codeCaptor.capture(),
                eq(Duration.ofMinutes(5))
        );

        // When
        ResultActions response = mockMvc.perform(patch("/api/v2/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "currentPassword", CURRENT_PASSWORD,
                        "newPassword", NEW_PASSWORD,
                        "challengeId", challengeId,
                        "code", codeCaptor.getValue()
                ))));

        // Then
        response.andExpectAll(
                status().isNoContent(),
                content().string(""),
                header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store"))
        );
        Account changedAccount = accountJpaRepository.findById(accountId).orElseThrow();
        String challengeStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM identity_service.email_verification_challenges WHERE id = ?",
                String.class,
                challengeId
        );
        thenSoftly(softly -> {
            softly.then(passwordHasher.matches(
                    NEW_PASSWORD,
                    changedAccount.getPasswordHash()
            )).isTrue();
            softly.then(tokensFor(accountId)).hasSize(1).allSatisfy(token -> {
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevocationReason())
                        .isEqualTo(RefreshTokenRevocationReason.PASSWORD_CHANGED);
            });
            softly.then(challengeStatus).isEqualTo("CONSUMED");
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

    @Test
    @DisplayName("같은 현재 비밀번호를 사용한 동시 변경 직렬화")
    void serializesConcurrentPasswordChanges() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        api.loginSuccessfully("user@example.com", CURRENT_PASSWORD);
        api.loginSuccessfully("user@example.com", CURRENT_PASSWORD);

        CountDownLatch firstChangeFinished = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondChangeStarted = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // 첫 변경이 계정 행 잠금을 유지한 채 커밋 직전 대기
        Future<?> firstChange = executor.submit(() -> transaction.executeWithoutResult(status -> {
            passwordChangeService.changePassword(
                    accountId,
                    CURRENT_PASSWORD,
                    FIRST_NEW_PASSWORD
            );
            firstChangeFinished.countDown();
            await(allowFirstCommit);
        }));
        await(firstChangeFinished);

        // When
        Future<Throwable> secondChange = executor.submit(() -> {
            secondChangeStarted.countDown();
            return catchThrowable(() -> passwordChangeService.changePassword(
                    accountId,
                    CURRENT_PASSWORD,
                    SECOND_NEW_PASSWORD
            ));
        });
        await(secondChangeStarted);

        // 첫 Transaction이 계정 행을 잠근 동안 두 번째 변경이 완료되지 않아야 함
        try {
            thenThrownBy(() -> secondChange.get(
                    LOCK_CHECK_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
            )).isInstanceOf(TimeoutException.class);
        } finally {
            allowFirstCommit.countDown();
        }

        firstChange.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        Throwable secondFailure = secondChange.get(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        Account changedAccount = accountJpaRepository.findById(accountId).orElseThrow();
        List<RefreshToken> tokens = tokensFor(accountId);

        // Then
        then(secondFailure).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.CURRENT_PASSWORD_MISMATCH)
        );
        thenSoftly(softly -> {
            softly.then(passwordHasher.matches(
                    FIRST_NEW_PASSWORD,
                    changedAccount.getPasswordHash()
            )).isTrue();
            softly.then(passwordHasher.matches(
                    SECOND_NEW_PASSWORD,
                    changedAccount.getPasswordHash()
            )).isFalse();
            softly.then(tokens).hasSize(2).allSatisfy(token -> {
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevocationReason())
                        .isEqualTo(RefreshTokenRevocationReason.PASSWORD_CHANGED);
            });
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과됐습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단됐습니다.", exception);
        }
    }
}
