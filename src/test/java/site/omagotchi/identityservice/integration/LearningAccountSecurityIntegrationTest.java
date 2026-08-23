package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class LearningAccountSecurityIntegrationTest {

    private static final String LEARNING_USERNAME = "learning-service";
    private static final String LEARNING_PASSWORD = "test-only-learning-identity-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AccountStateTestFixture accountStateFixture;

    @BeforeEach
    void setUp() {
        accountStateFixture = new AccountStateTestFixture(jdbcTemplate);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("계정 조회 API는 Learning Credential 요구")
    void requiresLearningCredential() throws Exception {
        // Given
        UUID accountId = UUID.randomUUID();

        // When
        ResultActions missingCredential = mockMvc.perform(
                get("/api/v1/internal/accounts/{accountId}", accountId)
        );
        ResultActions wrongCredential = mockMvc.perform(
                get("/api/v1/internal/accounts/{accountId}", accountId)
                        .with(httpBasic(LEARNING_USERNAME, "wrong-learning-password"))
        );

        // Then
        missingCredential.andExpectAll(
                status().isUnauthorized(),
                header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        startsWith("Basic realm=\"omagotchi-identity-learning\"")
                ),
                jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
        );
        wrongCredential.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
        );
    }

    @Test
    @DisplayName("Frontend와 Learning 서비스 인증 경계 분리")
    void separatesFrontendAndLearningCredentials() throws Exception {
        // When
        ResultActions frontendOnAccountQuery = mockMvc.perform(
                get("/api/v1/internal/accounts/{accountId}", UUID.randomUUID())
                        .with(httpBasic(
                                AuthApiTestClient.FRONTEND_USERNAME,
                                AuthApiTestClient.FRONTEND_PASSWORD
                        ))
        );
        ResultActions learningOnFrontendApi = mockMvc.perform(
                post("/api/v1/auth/login")
                        .with(httpBasic(LEARNING_USERNAME, LEARNING_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password-passphrase"
                                }
                                """)
        );

        // Then
        frontendOnAccountQuery.andExpect(status().isUnauthorized());
        learningOnFrontendApi.andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Learning 계정 단건 조회")
    void getsAccount() throws Exception {
        // Given
        Account account = saveAccount("active@example.com", "활성 사용자");

        // When & Then
        learningGet(account.getId()).andExpectAll(
                status().isOk(),
                jsonPath("$.accountId").value(account.getId().toString()),
                jsonPath("$.displayName").value("활성 사용자"),
                jsonPath("$.status").value("ACTIVE")
        );
    }

    @Test
    @DisplayName("존재하지 않는 Learning 계정 단건 조회 시 404")
    void returnsNotFoundForMissingAccount() throws Exception {
        // When & Then
        learningGet(UUID.randomUUID()).andExpectAll(
                status().isNotFound(),
                jsonPath("$.code").value("ACCOUNT_NOT_FOUND")
        );
    }

    @Test
    @DisplayName("Learning 계정 일괄 조회 결과에 존재하는 계정만 포함")
    void getsExistingAccountsInBatch() throws Exception {
        // Given
        Account active = saveAccount("active@example.com", "활성 사용자");
        Account withdrawn = saveAccount("withdrawn@example.com", "탈퇴 사용자");
        accountStateFixture.changeStatus(withdrawn.getId(), AccountStatus.WITHDRAWN);
        UUID missingId = UUID.randomUUID();

        // When
        ResultActions response = mockMvc.perform(
                post("/api/v1/internal/accounts/batch")
                        .with(httpBasic(LEARNING_USERNAME, LEARNING_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountIds": ["%s", "%s", "%s"]
                                }
                                """.formatted(active.getId(), withdrawn.getId(), missingId))
        );

        // Then
        response.andExpectAll(
                status().isOk(),
                jsonPath("$[*].accountId", containsInAnyOrder(
                        active.getId().toString(),
                        withdrawn.getId().toString()
                )),
                jsonPath("$[*].status", containsInAnyOrder("ACTIVE", "WITHDRAWN"))
        );
    }

    @Test
    @DisplayName("Learning 계정 일괄 조회는 요청 상한 100개까지 허용")
    void acceptsMaximumAccountBatch() throws Exception {
        // Given
        String accountIds = IntStream
                .rangeClosed(1, 100)
                .mapToObj(ignored -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        // When & Then
        mockMvc.perform(
                        post("/api/v1/internal/accounts/batch")
                                .with(httpBasic(LEARNING_USERNAME, LEARNING_PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"accountIds\":[" + accountIds + "]}")
                )
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$").isArray()
                );
    }

    @Test
    @DisplayName("Learning 계정 일괄 조회의 요청 상한 검증")
    void rejectsOversizedAccountBatch() throws Exception {
        // Given
        String accountIds = IntStream
                .rangeClosed(1, 101)
                .mapToObj(ignored -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        // When & Then
        mockMvc.perform(
                        post("/api/v1/internal/accounts/batch")
                                .with(httpBasic(LEARNING_USERNAME, LEARNING_PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"accountIds\":[" + accountIds + "]}")
                )
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("COMMON_INVALID_REQUEST")
                );
    }

    private ResultActions learningGet(UUID accountId) throws Exception {
        return mockMvc.perform(
                get("/api/v1/internal/accounts/{accountId}", accountId)
                        .with(httpBasic(LEARNING_USERNAME, LEARNING_PASSWORD))
        );
    }

    private Account saveAccount(String email, String name) {
        return accountJpaRepository.saveAndFlush(
                Account.register(email, "test-password-hash", name)
        );
    }
}
