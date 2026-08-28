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
import java.util.stream.Collectors;
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
class LearningAccountSecurityIT {

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
                .collect(Collectors.joining(","));

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
                .collect(Collectors.joining(","));

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

    @Test
    @DisplayName("Learning 계정 이름 검색")
    void searchesAccountsByName() throws Exception {
        // Given
        Account matched = saveAccount("name-match@example.com", "검색 대상 사용자");
        saveAccount("other@example.com", "다른 사용자");

        // When & Then
        learningSearch("  대상  ").andExpectAll(
                status().isOk(),
                jsonPath("$.length()").value(1),
                jsonPath("$[0].accountId").value(matched.getId().toString()),
                jsonPath("$[0].displayName").value("검색 대상 사용자"),
                jsonPath("$[0].email").value("name-match@example.com"),
                jsonPath("$[0].status").value("ACTIVE")
        );
    }

    @Test
    @DisplayName("Learning 계정 이메일 검색")
    void searchesAccountsByEmail() throws Exception {
        // Given
        Account matched = saveAccount("unique.email@example.com", "이메일 사용자");
        saveAccount("other@example.com", "다른 사용자");

        // When & Then
        learningSearch("UNIQUE.EMAIL").andExpectAll(
                status().isOk(),
                jsonPath("$.length()").value(1),
                jsonPath("$[0].accountId").value(matched.getId().toString()),
                jsonPath("$[0].email").value("unique.email@example.com")
        );
    }

    @Test
    @DisplayName("Learning 계정 검색 결과 없음")
    void returnsEmptyAccountSearchResult() throws Exception {
        learningSearch("존재하지않는검색어").andExpectAll(
                status().isOk(),
                jsonPath("$").isArray(),
                jsonPath("$").isEmpty()
        );
    }

    @Test
    @DisplayName("Learning 계정 검색 결과는 20개로 제한")
    void limitsAccountSearchResults() throws Exception {
        IntStream.range(0, 25).forEach(index ->
                saveAccount("limited-%02d@example.com".formatted(index), "제한 검색 사용자"));

        learningSearch("제한 검색").andExpectAll(
                status().isOk(),
                jsonPath("$.length()").value(20)
        );
    }

    @Test
    @DisplayName("Learning 계정 검색은 빈 검색어 거부")
    void rejectsBlankAccountSearchQuery() throws Exception {
        learningSearch("   ").andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("COMMON_INVALID_REQUEST")
        );
    }

    @Test
    @DisplayName("Learning 계정 검색 API는 Learning Credential 요구")
    void accountSearchRequiresLearningCredential() throws Exception {
        mockMvc.perform(get("/api/v1/internal/accounts/search").queryParam("query", "사용자"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions learningGet(UUID accountId) throws Exception {
        return mockMvc.perform(
                get("/api/v1/internal/accounts/{accountId}", accountId)
                        .with(httpBasic(LEARNING_USERNAME, LEARNING_PASSWORD))
        );
    }

    private ResultActions learningSearch(String query) throws Exception {
        return mockMvc.perform(
                get("/api/v1/internal/accounts/search")
                        .queryParam("query", query)
                        .with(httpBasic(LEARNING_USERNAME, LEARNING_PASSWORD))
        );
    }

    private Account saveAccount(String email, String name) {
        return accountJpaRepository.saveAndFlush(
                Account.register(email, "test-password-hash", name)
        );
    }
}
