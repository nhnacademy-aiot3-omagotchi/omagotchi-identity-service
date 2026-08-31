package site.omagotchi.identityservice.integration;

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
import site.omagotchi.identityservice.emailverification.application.EmailDeliveryException;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
@Execution(ExecutionMode.SAME_THREAD)
class EmailVerificationApiIT {

    private static final String ISSUE_AND_CONSUME_EMAIL = "issue-and-consume@example.com";
    private static final String FAILED_ATTEMPT_EMAIL = "failed-attempt@example.com";
    private static final String DELIVERY_FAILURE_EMAIL = "delivery-failure@example.com";
    private static final String FRONTEND_CREDENTIAL_EMAIL = "frontend-credential@example.com";
    private static final String PASSWORD = "password-passphrase";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailVerificationMailSender mailSender;

    @Test
    @DisplayName("회원가입 OTP 발급·쿨다운·검증·일회 소비")
    void issuesVerifiesAndConsumesSignupOtp() throws Exception {
        // Given
        deleteTestData(ISSUE_AND_CONSUME_EMAIL);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        // When
        String issueResponse = issueSignupOtp(ISSUE_AND_CONSUME_EMAIL)
                .andExpectAll(
                        status().isAccepted(),
                        header().string(HttpHeaders.CACHE_CONTROL, "no-store"),
                        jsonPath("$.expiresInSeconds").value(allOf(
                                greaterThanOrEqualTo(1),
                                lessThanOrEqualTo(300)
                        ))
                )
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID challengeId = UUID.fromString(
                objectMapper.readTree(issueResponse).get("challengeId").asString()
        );

        // Then
        verify(mailSender).sendVerificationCode(
                eq(challengeId),
                eq(ISSUE_AND_CONSUME_EMAIL),
                codeCaptor.capture(),
                eq(Duration.ofMinutes(5))
        );

        issueSignupOtp(ISSUE_AND_CONSUME_EMAIL).andExpectAll(
                status().isTooManyRequests(),
                header().string(HttpHeaders.RETRY_AFTER, "60"),
                jsonPath("$.code").value("EMAIL_VERIFICATION_COOLDOWN_ACTIVE")
        );

        signup(ISSUE_AND_CONSUME_EMAIL, challengeId, codeCaptor.getValue()).andExpectAll(
                status().isCreated(),
                jsonPath("$.email").value(ISSUE_AND_CONSUME_EMAIL)
        );
        signup(ISSUE_AND_CONSUME_EMAIL, challengeId, codeCaptor.getValue()).andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID_CHALLENGE")
        );

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM identity_service.email_verification_challenges WHERE id = ?",
                String.class,
                challengeId
        );
        then(status).isEqualTo("CONSUMED");
    }

    @Test
    @DisplayName("잘못된 OTP 응답 뒤 실패 횟수 Commit")
    void commitsFailedAttemptBeforeErrorResponse() throws Exception {
        // Given
        deleteTestData(FAILED_ATTEMPT_EMAIL);
        JsonNode issued = objectMapper.readTree(issueSignupOtp(FAILED_ATTEMPT_EMAIL)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        UUID challengeId = UUID.fromString(issued.get("challengeId").asString());

        // When
        signup(FAILED_ATTEMPT_EMAIL, challengeId, "000000").andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID_CHALLENGE")
        );

        // Then
        Integer failedAttempts = jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM identity_service.email_verification_challenges WHERE id = ?",
                Integer.class,
                challengeId
        );
        then(failedAttempts).isEqualTo(1);
    }

    @Test
    @DisplayName("메일 사업자 실패를 503으로 응답하고 즉시 재발급 허용")
    void releasesCooldownAfterDeliveryFailure() throws Exception {
        // Given
        deleteTestData(DELIVERY_FAILURE_EMAIL);
        doThrow(new EmailDeliveryException("provider unavailable", new RuntimeException()))
                .doNothing()
                .when(mailSender)
                .sendVerificationCode(
                        any(),
                        eq(DELIVERY_FAILURE_EMAIL),
                        any(),
                        eq(Duration.ofMinutes(5))
                );

        // When
        issueSignupOtp(DELIVERY_FAILURE_EMAIL).andExpectAll(
                status().isServiceUnavailable(),
                jsonPath("$.code").value("EMAIL_VERIFICATION_DELIVERY_UNAVAILABLE")
        );
        issueSignupOtp(DELIVERY_FAILURE_EMAIL).andExpect(status().isAccepted());

        // Then
        Integer failedDeliveries = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM identity_service.email_verification_challenges
                WHERE delivery_status = 'FAILED'
                  AND email = ?
                """,
                Integer.class,
                DELIVERY_FAILURE_EMAIL
        );
        then(failedDeliveries).isEqualTo(1);
    }

    @Test
    @DisplayName("v2 회원가입 OTP API는 Frontend Credential 요구")
    void requiresFrontendCredential() throws Exception {
        // Given
        String requestBody = issueBody(FRONTEND_CREDENTIAL_EMAIL);

        // When
        mockMvc.perform(post("/api/v2/auth/signup/email-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then
                .andExpectAll(
                        status().isUnauthorized(),
                        header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Basic")),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                );
    }

    private org.springframework.test.web.servlet.ResultActions issueSignupOtp(String email)
            throws Exception {
        return mockMvc.perform(post("/api/v2/auth/signup/email-otp")
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        AuthApiTestClient.FRONTEND_PASSWORD
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueBody(email)));
    }

    private org.springframework.test.web.servlet.ResultActions signup(
            String email,
            UUID challengeId,
            String code
    ) throws Exception {
        return mockMvc.perform(post("/api/v2/auth/signup")
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        AuthApiTestClient.FRONTEND_PASSWORD
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", PASSWORD,
                        "name", "member",
                        "challengeId", challengeId,
                        "code", code
                ))));
    }

    private String issueBody(String email) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", PASSWORD,
                "name", "member"
        ));
    }

    private void deleteTestData(String email) {
        jdbcTemplate.update("""
                DELETE FROM identity_service.refresh_tokens
                WHERE account_id IN (
                    SELECT id
                    FROM identity_service.accounts
                    WHERE email = ?
                )
                """, email);
        jdbcTemplate.update(
                "DELETE FROM identity_service.email_verification_challenges WHERE email = ?",
                email
        );
        jdbcTemplate.update(
                "DELETE FROM identity_service.email_verification_scopes WHERE email = ?",
                email
        );
        jdbcTemplate.update(
                "DELETE FROM identity_service.accounts WHERE email = ?",
                email
        );
    }
}
