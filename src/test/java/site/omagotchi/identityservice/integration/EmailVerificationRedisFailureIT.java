package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import site.omagotchi.identityservice.email.application.EmailVerificationErrorCode;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EmailVerificationRedisFailureIT {

    private static final String ACCOUNT_EMAIL = "user@example.com";
    private static final String SIGNUP_EMAIL = "new-user@example.com";
    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    @Qualifier("redisContainer")
    private GenericContainer<?> redisContainer;

    private AuthApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper, emailVerificationRepository);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @DisplayName("실제 Redis 중단 시 OTP API는 503이고 최종 요청의 DB 변경은 Rollback")
    void returnsServiceUnavailableAndRollsBackDatabaseChangesWhenRedisStops() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully(ACCOUNT_EMAIL);
        AuthApiTestClient.TokenBundle firstLogin =
                api.loginSuccessfully(ACCOUNT_EMAIL, CURRENT_PASSWORD);
        api.loginSuccessfully(ACCOUNT_EMAIL, CURRENT_PASSWORD);
        AuthApiTestClient.OtpProof signupProof =
                api.otp(SIGNUP_EMAIL, VerificationPurpose.SIGN_UP);
        AuthApiTestClient.OtpProof passwordChangeProof =
                api.otp(ACCOUNT_EMAIL, VerificationPurpose.PASSWORD_CHANGE);
        String originalPasswordHash = accountJpaRepository.findById(accountId)
                .orElseThrow()
                .getPasswordHash();

        redisContainer.stop();

        // When
        ResultActions signupOtpResponse = requestSignupEmailOtp();
        ResultActions passwordOtpResponse =
                requestPasswordChangeEmailOtp(firstLogin.accessToken());
        ResultActions signupResponse = api.signUpWithCode(
                SIGNUP_EMAIL,
                CURRENT_PASSWORD,
                "신규 사용자",
                signupProof.challengeId(),
                signupProof.code()
        );
        ResultActions passwordChangeResponse = api.changePasswordWithCode(
                firstLogin.accessToken(),
                CURRENT_PASSWORD,
                NEW_PASSWORD,
                passwordChangeProof.challengeId(),
                passwordChangeProof.code()
        );

        // Then
        thenUnavailable(signupOtpResponse, "/api/v2/auth/signup/email-otp");
        thenUnavailable(passwordOtpResponse, "/api/v2/users/me/password/email-otp");
        thenUnavailable(signupResponse, "/api/v2/auth/signup");
        thenUnavailable(passwordChangeResponse, "/api/v2/users/me/password");

        then(accountJpaRepository.findByEmail(SIGNUP_EMAIL)).isEmpty();
        Account rolledBackAccount = accountJpaRepository.findById(accountId).orElseThrow();
        then(rolledBackAccount.getPasswordHash()).isEqualTo(originalPasswordHash);
        then(passwordHasher.matches(
                CURRENT_PASSWORD,
                rolledBackAccount.getPasswordHash()
        )).isTrue();
        then(passwordHasher.matches(
                NEW_PASSWORD,
                rolledBackAccount.getPasswordHash()
        )).isFalse();
        then(tokensFor(accountId)).hasSize(2).allSatisfy(token -> {
            then(token.isRevoked()).isFalse();
            then(token.getRevocationReason()).isNull();
        });
    }

    private ResultActions requestSignupEmailOtp() throws Exception {
        return mockMvc.perform(post("/api/v2/auth/signup/email-otp")
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        AuthApiTestClient.FRONTEND_PASSWORD
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "name": "신규 사용자"
                        }
                        """.formatted(SIGNUP_EMAIL, CURRENT_PASSWORD)));
    }

    private ResultActions requestPasswordChangeEmailOtp(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/v2/users/me/password/email-otp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private void thenUnavailable(ResultActions response, String path) throws Exception {
        response.andExpectAll(
                status().isServiceUnavailable(),
                jsonPath("$.code").value(EmailVerificationErrorCode.UNAVAILABLE.code()),
                jsonPath("$.message").value(EmailVerificationErrorCode.UNAVAILABLE.message()),
                jsonPath("$.path").value(path)
        );
    }

    private List<RefreshToken> tokensFor(UUID accountId) {
        return refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getAccountId().equals(accountId))
                .toList();
    }
}
