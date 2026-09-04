package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * 관리자 사용자 목록 API의 인증·인가 경계와 조회 계약 검증
 *
 * - 전체 계정을 열람할 수 있는 유일한 경로이므로 인가 회귀를 우선 고정
 * - Access JWT의 role Claim이 낡았을 때의 DB 재검증까지 포함
 */
class AdminAccountApiIT extends BaseIntegrationTest {

    private static final String ADMIN_USERS_PATH = "/api/v1/admin/users";
    private static final String PASSWORD = "password-passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AuthApiTestClient api;
    private AccountStateTestFixture accountStateFixture;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        accountStateFixture = new AccountStateTestFixture(jdbcTemplate);
        cleanDatabase();
    }

    @Test
    @DisplayName("관리자의 사용자 목록 조회는 최신 가입순 페이지와 전체 건수 반환")
    void returnsAccountPageForSystemAdmin() throws Exception {
        // Given
        signUp("first@example.com", "김일번");
        signUp("second@example.com", "이이번");
        String accessToken = systemAdminAccessToken("admin@example.com");

        // When
        ResultActions response = getUsers(accessToken, "size", "2");

        // Then
        response.andExpectAll(
                status().isOk(),
                jsonPath("$.page.number").value(0),
                jsonPath("$.page.size").value(2),
                jsonPath("$.page.totalElements").value(3),
                jsonPath("$.page.totalPages").value(2),
                jsonPath("$.items.length()").value(2),
                // 가장 마지막에 가입한 관리자 계정이 첫 행
                jsonPath("$.items[0].email").value("admin@example.com"),
                jsonPath("$.items[0].role").value(GlobalRole.SYSTEM_ADMIN.name()),
                jsonPath("$.items[0].status").value(AccountStatus.ACTIVE.name()),
                jsonPath("$.items[0].createdAt").isNotEmpty(),
                jsonPath("$.items[0].failedLoginAttempts").value(0),
                jsonPath("$.items[0].lockedUntil").value(nullValue()),
                jsonPath("$.items[0].withdrawnAt").value(nullValue()),
                jsonPath("$.items[1].email").value("second@example.com")
        );
    }

    @Test
    @DisplayName("응답 본문에 비밀번호 Hash 등 인증 근거값 미포함")
    void doesNotExposeAuthenticationSecrets() throws Exception {
        // Given
        signUp("user@example.com", "홍길동");
        String accessToken = systemAdminAccessToken("admin@example.com");

        // When
        String body = getUsers(accessToken)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then
        then(body)
                .doesNotContain("passwordHash")
                .doesNotContain("password_hash")
                .doesNotContain("$2a$")
                .doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("Access JWT 없는 요청의 401 응답")
    void requiresAccessToken() throws Exception {
        // When
        ResultActions response = mockMvc.perform(get(ADMIN_USERS_PATH));

        // Then
        response.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
        );
    }

    @Test
    @DisplayName("USER 권한 Access JWT의 403 응답")
    void rejectsUserRole() throws Exception {
        // Given
        signUp("user@example.com", "홍길동");
        String accessToken = api.loginSuccessfully("user@example.com", PASSWORD).accessToken();

        // When
        ResultActions response = getUsers(accessToken);

        // Then
        response.andExpectAll(
                status().isForbidden(),
                jsonPath("$.code").value("AUTH_ACCESS_DENIED")
        );
    }

    @Test
    @DisplayName("Token 발급 이후 강등된 관리자는 DB 재검증으로 403")
    void rejectsDemotedAdminWithStaleToken() throws Exception {
        // Given
        UUID adminId = signUp("admin@example.com", "관리자");
        accountStateFixture.changeGlobalRole(adminId, GlobalRole.SYSTEM_ADMIN);
        String accessToken = api.loginSuccessfully("admin@example.com", PASSWORD).accessToken();

        // 발급된 Access JWT의 role Claim은 SYSTEM_ADMIN으로 남아 Filter Chain을 통과한다.
        accountStateFixture.changeGlobalRole(adminId, GlobalRole.USER);

        // When
        ResultActions response = getUsers(accessToken);

        // Then
        response.andExpectAll(
                status().isForbidden(),
                jsonPath("$.code").value("ACCOUNT_ADMIN_ACCESS_NOT_ALLOWED")
        );
    }

    @Test
    @DisplayName("Token 발급 이후 비활성화된 관리자는 DB 재검증으로 403")
    void rejectsDisabledAdminWithStaleToken() throws Exception {
        // Given
        UUID adminId = signUp("admin@example.com", "관리자");
        accountStateFixture.changeGlobalRole(adminId, GlobalRole.SYSTEM_ADMIN);
        String accessToken = api.loginSuccessfully("admin@example.com", PASSWORD).accessToken();
        accountStateFixture.changeStatus(adminId, AccountStatus.DISABLED);

        // When
        ResultActions response = getUsers(accessToken);

        // Then
        response.andExpectAll(
                status().isForbidden(),
                jsonPath("$.code").value("ACCOUNT_ADMIN_ACCESS_NOT_ALLOWED")
        );
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
            "size, 101",
            "size, 0",
            "page, -1",
            "sort, passwordHash",
            "sort, CREATED_AT",
            "status, UNKNOWN",
            "role, ROOT",
            "query, '   '"
    })
    @DisplayName("허용 범위를 벗어난 조회 조건의 400 응답")
    void rejectsOutOfContractParameters(String name, String value) throws Exception {
        // Given
        String accessToken = systemAdminAccessToken("admin@example.com");

        // When
        ResultActions response = getUsers(accessToken, name, value);

        // Then
        response.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("COMMON_INVALID_REQUEST")
        );
    }

    @Test
    @DisplayName("이름·이메일 부분 일치 검색")
    void searchesByNameOrEmail() throws Exception {
        // Given
        signUp("gildong@example.com", "홍길동");
        signUp("chulsoo@example.com", "김철수");
        String accessToken = systemAdminAccessToken("admin@example.com");

        // When
        ResultActions byName = getUsers(accessToken, "query", "길동");
        ResultActions byEmail = getUsers(accessToken, "query", "CHULSOO");

        // Then
        byName.andExpectAll(
                status().isOk(),
                jsonPath("$.page.totalElements").value(1),
                jsonPath("$.items[0].email").value("gildong@example.com")
        );
        byEmail.andExpectAll(
                status().isOk(),
                jsonPath("$.page.totalElements").value(1),
                jsonPath("$.items[0].email").value("chulsoo@example.com")
        );
    }

    @Test
    @DisplayName("LIKE 메타문자는 리터럴로 처리해 전체 매칭으로 확장되지 않음")
    void escapesLikeMetaCharacters() throws Exception {
        // Given
        signUp("plain@example.com", "홍길동");
        signUp("percent@example.com", "100% 달성");
        String accessToken = systemAdminAccessToken("admin@example.com");

        // When
        ResultActions response = getUsers(accessToken, "query", "%");

        // Then
        response.andExpectAll(
                status().isOk(),
                jsonPath("$.page.totalElements").value(1),
                jsonPath("$.items[0].email").value("percent@example.com")
        );
    }

    @Test
    @DisplayName("계정 상태·전역 권한 필터 적용")
    void filtersByStatusAndRole() throws Exception {
        // Given
        UUID disabledId = signUp("disabled@example.com", "정지자");
        signUp("active@example.com", "활성자");
        accountStateFixture.changeStatus(disabledId, AccountStatus.DISABLED);
        String accessToken = systemAdminAccessToken("admin@example.com");

        // When
        ResultActions byStatus = getUsers(accessToken, "status", AccountStatus.DISABLED.name());
        ResultActions byRole = getUsers(accessToken, "role", GlobalRole.SYSTEM_ADMIN.name());

        // Then
        byStatus.andExpectAll(
                status().isOk(),
                jsonPath("$.page.totalElements").value(1),
                jsonPath("$.items[0].email").value("disabled@example.com")
        );
        byRole.andExpectAll(
                status().isOk(),
                jsonPath("$.page.totalElements").value(1),
                jsonPath("$.items[0].email").value("admin@example.com")
        );
    }

    @Test
    @DisplayName("가입 시각이 같아도 페이지 경계에서 계정이 중복·누락되지 않음")
    void keepsStablePageBoundaryForIdenticalCreatedAt() throws Exception {
        // Given
        for (int index = 0; index < 5; index++) {
            signUp("user" + index + "@example.com", "동시가입" + index);
        }
        String accessToken = systemAdminAccessToken("admin@example.com");
        // 모든 계정의 가입 시각을 동일하게 만들어 정렬 Tie-breaker 부재를 노출시킨다.
        jdbcTemplate.update(
                "UPDATE identity_service.accounts SET created_at = TIMESTAMPTZ '2026-01-01 00:00:00+00'");

        // When
        List<String> collected = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            String body = getUsers(accessToken, "page", String.valueOf(page), "size", "2")
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode items = objectMapper.readTree(body).get("items");
            for (int index = 0; index < items.size(); index++) {
                collected.add(items.get(index).get("accountId").asString());
            }
        }

        // Then
        then(collected).hasSize(6).doesNotHaveDuplicates();
    }

    private ResultActions getUsers(String accessToken, String... params) throws Exception {
        var request = get(ADMIN_USERS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        for (int index = 0; index < params.length; index += 2) {
            request = request.param(params[index], params[index + 1]);
        }
        return mockMvc.perform(request);
    }

    private String systemAdminAccessToken(String email) throws Exception {
        UUID adminId = signUp(email, "관리자");
        accountStateFixture.changeGlobalRole(adminId, GlobalRole.SYSTEM_ADMIN);
        return api.loginSuccessfully(email, PASSWORD).accessToken();
    }

    private UUID signUp(String email, String name) throws Exception {
        String body = api.signUp(email, PASSWORD, name)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("userId").asString());
    }
}
