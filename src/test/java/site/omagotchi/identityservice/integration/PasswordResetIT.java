package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
@Execution(ExecutionMode.SAME_THREAD)
class PasswordResetIT {

    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @MockitoBean
    private EmailVerificationMailSender mailSender;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final List<String> testEmails = new ArrayList<>();
    private AuthApiTestClient authApi;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiTestClient(mockMvc, objectMapper);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        testEmails.forEach(this::deleteTestData);
    }

    @Test
    @DisplayName("미가입 이메일도 계정 조회 오류 없이 OTP 발급")
    void issuesOtpForUnregisteredEmail() throws Exception {
        // Given
        String email = uniqueEmail("unregistered-issue");

        // When
        IssuedOtp issued = issuePasswordResetOtp(email);

        // Then
        then(issued.challengeId()).isNotNull();
        then(issued.code()).matches("\\d{6}");
    }

    @Test
    @DisplayName("OTP로 비밀번호 재설정 후 기존 Session 폐기")
    void resetsPasswordAndRevokesRefreshSessions() throws Exception {
        // Given
        String email = uniqueEmail("success");
        UUID accountId = authApi.signupSuccessfully(email);
        AuthApiTestClient.TokenBundle session = authApi.loginSuccessfully(
                email,
                CURRENT_PASSWORD
        );
        IssuedOtp issued = issuePasswordResetOtp(email);

        // When
        resetPassword(email, NEW_PASSWORD, issued.challengeId(), issued.code())
                .andExpectAll(
                        status().isNoContent(),
                        header().string(HttpHeaders.CACHE_CONTROL, "no-store")
                );

        // Then
        authApi.login(email, CURRENT_PASSWORD).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS")
        );
        authApi.refresh(session.refreshToken()).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        then(refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getAccountId().equals(accountId)))
                .singleElement()
                .satisfies(token -> {
                    then(token.getAccountId()).isEqualTo(accountId);
                    then(token.getRevocationReason())
                            .isEqualTo(RefreshTokenRevocationReason.PASSWORD_RESET);
                });
        authApi.loginSuccessfully(email, NEW_PASSWORD);

        Map<String, Object> account = jdbcTemplate.queryForMap(
                """
                SELECT status, failed_login_attempts, locked_until
                FROM identity_service.accounts
                WHERE id = ?
                """,
                accountId
        );
        String challengeStatus = challengeStatus(issued.challengeId());
        thenSoftly(softly -> {
            softly.then(account)
                    .containsEntry("status", "ACTIVE")
                    .containsEntry("failed_login_attempts", 0);
            softly.then(account.get("locked_until")).isNull();
            softly.then(challengeStatus).isEqualTo("CONSUMED");
        });
    }

    @Test
    @DisplayName("잠긴 계정 재설정 시 ACTIVE 복구와 로그인 실패 상태 초기화")
    void unlocksAccountAfterPasswordReset() throws Exception {
        // Given
        String email = uniqueEmail("locked");
        UUID accountId = authApi.signupSuccessfully(email);
        jdbcTemplate.update(
                """
                UPDATE identity_service.accounts
                SET status = 'LOCKED',
                    failed_login_attempts = 5,
                    locked_until = CURRENT_TIMESTAMP + INTERVAL '10 minutes'
                WHERE id = ?
                """,
                accountId
        );
        IssuedOtp issued = issuePasswordResetOtp(email);

        // When
        resetPassword(email, NEW_PASSWORD, issued.challengeId(), issued.code())
                .andExpect(status().isNoContent());

        // Then
        Map<String, Object> account = jdbcTemplate.queryForMap(
                """
                SELECT status, failed_login_attempts, locked_until
                FROM identity_service.accounts
                WHERE id = ?
                """,
                accountId
        );
        then(account)
                .containsEntry("status", "ACTIVE")
                .containsEntry("failed_login_attempts", 0);
        then(account.get("locked_until")).isNull();
        authApi.loginSuccessfully(email, NEW_PASSWORD);
    }

    @Test
    @DisplayName("없는 계정과 비활성 계정은 같은 재설정 오류")
    void hidesAccountExistenceAndStatus() throws Exception {
        // Given
        String missingEmail = uniqueEmail("missing");
        String disabledEmail = uniqueEmail("disabled");
        UUID disabledAccountId = authApi.signupSuccessfully(disabledEmail);
        jdbcTemplate.update(
                "UPDATE identity_service.accounts SET status = 'DISABLED' WHERE id = ?",
                disabledAccountId
        );
        IssuedOtp missingOtp = issuePasswordResetOtp(missingEmail);
        IssuedOtp disabledOtp = issuePasswordResetOtp(disabledEmail);

        // When
        ResultActions missing = resetPassword(
                missingEmail,
                NEW_PASSWORD,
                missingOtp.challengeId(),
                missingOtp.code()
        );
        ResultActions disabled = resetPassword(
                disabledEmail,
                NEW_PASSWORD,
                disabledOtp.challengeId(),
                disabledOtp.code()
        );

        // Then
        missing.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("AUTH_PASSWORD_RESET_INVALID")
        );
        disabled.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("AUTH_PASSWORD_RESET_INVALID")
        );
        then(challengeStatus(missingOtp.challengeId())).isEqualTo("CONSUMED");
        then(challengeStatus(disabledOtp.challengeId())).isEqualTo("CONSUMED");
    }

    @Test
    @DisplayName("기존 비밀번호 재사용은 일반화 오류이며 Challenge로 다시 시도 가능")
    void keepsChallengeUsableAfterUnchangedPasswordRejection() throws Exception {
        // Given
        String email = uniqueEmail("unchanged-password");
        authApi.signupSuccessfully(email);
        IssuedOtp issued = issuePasswordResetOtp(email);

        // When
        resetPassword(email, CURRENT_PASSWORD, issued.challengeId(), issued.code())
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("AUTH_PASSWORD_RESET_INVALID")
                );

        // Then
        then(challengeStatus(issued.challengeId())).isEqualTo("OPEN");
        resetPassword(email, NEW_PASSWORD, issued.challengeId(), issued.code())
                .andExpect(status().isNoContent());
        authApi.loginSuccessfully(email, NEW_PASSWORD);
    }

    @Test
    @DisplayName("잘못된 OTP 오류 뒤 실패 횟수 Commit")
    void commitsFailedOtpAttempt() throws Exception {
        // Given
        String email = uniqueEmail("invalid-code");
        authApi.signupSuccessfully(email);
        IssuedOtp issued = issuePasswordResetOtp(email);
        String wrongCode = issued.code().equals("000000") ? "111111" : "000000";

        // When
        resetPassword(email, NEW_PASSWORD, issued.challengeId(), wrongCode)
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("AUTH_PASSWORD_RESET_INVALID")
                );

        // Then
        Integer failedAttempts = jdbcTemplate.queryForObject(
                """
                SELECT failed_attempts
                FROM identity_service.email_verification_challenges
                WHERE id = ?
                """,
                Integer.class,
                issued.challengeId()
        );
        then(failedAttempts).isEqualTo(1);
    }

    @Test
    @DisplayName("PASSWORD_RESET 발급 뒤 SIGNUP 발급도 공유 쿨다운으로 거부")
    void sharesCooldownAcrossPurposes() throws Exception {
        // Given
        String email = uniqueEmail("shared-cooldown");
        issuePasswordResetOtp(email);

        // When
        ResultActions signupIssue = issueSignupOtp(email);

        // Then
        signupIssue.andExpectAll(
                status().isTooManyRequests(),
                header().string(HttpHeaders.RETRY_AFTER, "60"),
                jsonPath("$.code").value("EMAIL_VERIFICATION_COOLDOWN_ACTIVE")
        );
        verify(mailSender, times(1)).sendVerificationCode(
                org.mockito.ArgumentMatchers.any(),
                eq(email),
                org.mockito.ArgumentMatchers.any(),
                eq(Duration.ofMinutes(5))
        );
    }

    @Test
    @DisplayName("동일 Challenge 동시 재설정은 한 요청만 성공")
    void consumesChallengeOnlyOnceUnderConcurrency() throws Exception {
        // Given
        String email = uniqueEmail("concurrent");
        authApi.signupSuccessfully(email);
        IssuedOtp issued = issuePasswordResetOtp(email);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> first = submitReset(
                ready,
                start,
                email,
                issued
        );
        Future<Integer> second = submitReset(
                ready,
                start,
                email,
                issued
        );
        await(ready);

        // When
        start.countDown();
        List<Integer> statuses = new ArrayList<>(List.of(
                first.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                second.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        ));
        statuses.sort(Integer::compareTo);

        // Then
        then(statuses).containsExactly(204, 400);
        then(challengeStatus(issued.challengeId())).isEqualTo("CONSUMED");
    }

    @Test
    @DisplayName("비밀번호 재설정 API는 Frontend Credential 요구")
    void requiresFrontendCredential() throws Exception {
        // Given
        String email = uniqueEmail("credential");

        // When
        // Then
        mockMvc.perform(post("/api/v2/auth/password-reset/email-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpectAll(
                        status().isUnauthorized(),
                        header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Basic")),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                );
    }

    private IssuedOtp issuePasswordResetOtp(String email) throws Exception {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        MvcResult response = mockMvc.perform(post("/api/v2/auth/password-reset/email-otp")
                        .with(httpBasic(
                                AuthApiTestClient.FRONTEND_USERNAME,
                                AuthApiTestClient.FRONTEND_PASSWORD
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpectAll(
                        status().isAccepted(),
                        header().string(HttpHeaders.CACHE_CONTROL, "no-store"),
                        jsonPath("$.expiresInSeconds").value(greaterThanOrEqualTo(1)),
                        jsonPath("$.expiresInSeconds").value(lessThanOrEqualTo(300))
                )
                .andReturn();
        JsonNode body = objectMapper.readTree(
                response.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
        UUID challengeId = UUID.fromString(body.get("challengeId").asString());
        verify(mailSender).sendVerificationCode(
                eq(challengeId),
                eq(email),
                codeCaptor.capture(),
                eq(Duration.ofMinutes(5))
        );
        return new IssuedOtp(challengeId, codeCaptor.getValue());
    }

    private ResultActions resetPassword(
            String email,
            String newPassword,
            UUID challengeId,
            String code
    ) throws Exception {
        return mockMvc.perform(patch("/api/v2/auth/password-reset")
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        AuthApiTestClient.FRONTEND_PASSWORD
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "newPassword", newPassword,
                        "challengeId", challengeId,
                        "code", code
                ))));
    }

    private ResultActions issueSignupOtp(String email) throws Exception {
        return mockMvc.perform(post("/api/v2/auth/signup/email-otp")
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        AuthApiTestClient.FRONTEND_PASSWORD
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", CURRENT_PASSWORD,
                        "name", "member"
                ))));
    }

    private Future<Integer> submitReset(
            CountDownLatch ready,
            CountDownLatch start,
            String email,
            IssuedOtp issued
    ) {
        return executor.submit(() -> {
            ready.countDown();
            await(start);
            return resetPassword(
                    email,
                    NEW_PASSWORD,
                    issued.challengeId(),
                    issued.code()
            ).andReturn().getResponse().getStatus();
        });
    }

    private String challengeStatus(UUID challengeId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM identity_service.email_verification_challenges
                WHERE id = ?
                """,
                String.class,
                challengeId
        );
    }

    private String uniqueEmail(String scenario) {
        String email = scenario + "+" + UUID.randomUUID() + "@example.com";
        testEmails.add(email);
        return email;
    }

    private void deleteTestData(String email) {
        jdbcTemplate.update(
                """
                DELETE FROM identity_service.refresh_tokens
                WHERE account_id IN (
                    SELECT id FROM identity_service.accounts WHERE email = ?
                )
                """,
                email
        );
        jdbcTemplate.update(
                "DELETE FROM identity_service.email_verification_challenges WHERE email = ?",
                email
        );
        jdbcTemplate.update(
                "DELETE FROM identity_service.email_verification_scopes WHERE email = ?",
                email
        );
        jdbcTemplate.update(
                "DELETE FROM identity_service.email_delivery_cooldowns WHERE email = ?",
                email
        );
        jdbcTemplate.update(
                "DELETE FROM identity_service.accounts WHERE email = ?",
                email
        );
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

    private record IssuedOtp(UUID challengeId, String code) {
    }
}
