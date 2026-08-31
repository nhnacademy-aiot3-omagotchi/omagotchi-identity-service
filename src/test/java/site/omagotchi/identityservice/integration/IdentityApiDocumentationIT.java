package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.headers.RequestHeadersSnippet;
import org.springframework.restdocs.headers.ResponseHeadersSnippet;
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor;
import org.springframework.restdocs.operation.preprocess.OperationResponsePreprocessor;
import org.springframework.restdocs.payload.RequestFieldsSnippet;
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
@Import({TestcontainersConfig.class, TestJwtConfig.class})
@DisplayName("Identity 전체 API 문서")
class IdentityApiDocumentationIT {

    private static final String PASSWORD = "password-passphrase";
    private static final String LEARNING_USERNAME = "learning-service";
    private static final String LEARNING_PASSWORD = "test-only-learning-identity-password";
    private static final Pattern ACCESS_TOKEN_JSON = Pattern.compile(
            "\"accessToken\"\\s*:\\s*\"[^\"]*\""
    );
    private static final Pattern REFRESH_TOKEN_JSON = Pattern.compile(
            "\"refreshToken\"\\s*:\\s*\"[^\"]*\""
    );
    private static final Pattern PASSWORD_JSON = Pattern.compile(
            "\"password\"\\s*:\\s*\"[^\"]*\""
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    private AuthApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 성공 계약")
    void documentsSignup() throws Exception {
        api.signUp("user@example.com", PASSWORD, "홍길동")
                .andExpect(status().isCreated())
                .andDo(document(
                        "auth/signup/success",
                        frontendRequest(),
                        documentedResponse(),
                        frontendCredentialHeader(),
                        requestFields(
                                fieldWithPath("email").description("가입할 이메일"),
                                fieldWithPath("password").description("15~64자 비밀번호. UTF-8 기준 최대 72바이트"),
                                fieldWithPath("name").description("앞뒤 공백을 제외한 1~30자 이름")
                        ),
                        accountResponseFields()
                ));
    }

    @Test
    @DisplayName("중복 이메일 회원가입 오류 계약")
    void documentsDuplicateSignup() throws Exception {
        api.signupSuccessfully("user@example.com");

        api.signUp("user@example.com")
                .andExpectAll(
                        status().isConflict(),
                        jsonPath("$.code").value("ACCOUNT_DUPLICATE_EMAIL")
                )
                .andDo(document(
                        "auth/signup/duplicate-email",
                        frontendRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("로그인 성공 계약")
    void documentsLogin() throws Exception {
        api.signupSuccessfully("user@example.com");

        api.login("user@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andDo(document(
                        "auth/login/success",
                        frontendRequest(),
                        documentedResponse(),
                        frontendCredentialHeader(),
                        noStoreResponseHeader(),
                        requestFields(
                                fieldWithPath("email").description("가입 이메일"),
                                fieldWithPath("password").description("비밀번호")
                        ),
                        tokenResponseFields()
                ));
    }

    @Test
    @DisplayName("잘못된 로그인 자격 증명 오류 계약")
    void documentsInvalidLogin() throws Exception {
        api.signupSuccessfully("user@example.com");

        api.login("user@example.com", "wrong-password1")
                .andExpectAll(
                        status().isUnauthorized(),
                        jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS")
                )
                .andDo(document(
                        "auth/login/invalid-credentials",
                        frontendRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("Refresh Token 회전 성공 계약")
    void documentsRefresh() throws Exception {
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                PASSWORD
        );

        api.refresh(login.refreshToken())
                .andExpect(status().isOk())
                .andDo(document(
                        "auth/refresh/success",
                        frontendRequest(),
                        documentedResponse(),
                        frontendCredentialHeader(),
                        noStoreResponseHeader(),
                        refreshTokenRequestFields(),
                        tokenResponseFields()
                ));
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token 오류 계약")
    void documentsInvalidRefreshToken() throws Exception {
        api.refresh("")
                .andExpectAll(
                        status().isUnauthorized(),
                        jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
                )
                .andDo(document(
                        "auth/refresh/invalid-token",
                        frontendRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("로그아웃 성공 계약")
    void documentsLogout() throws Exception {
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                PASSWORD
        );

        api.logout(login.refreshToken())
                .andExpect(status().isNoContent())
                .andDo(document(
                        "auth/logout/success",
                        frontendRequest(),
                        documentedResponse(),
                        frontendCredentialHeader(),
                        noStoreResponseHeader(),
                        refreshTokenRequestFields()
                ));
    }

    @Test
    @DisplayName("Frontend Credential 누락 오류 계약")
    void documentsMissingFrontendCredential() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password-passphrase"
                                }
                                """))
                .andExpectAll(
                        status().isUnauthorized(),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                )
                .andDo(document(
                        "security/frontend-authentication-required",
                        plainRequest(),
                        documentedResponse(),
                        responseHeaders(
                                headerWithName(HttpHeaders.WWW_AUTHENTICATE)
                                        .description("Frontend HTTP Basic 인증 challenge")
                        ),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("본인 계정 조회 성공 계약")
    void documentsCurrentAccount() throws Exception {
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                PASSWORD
        );

        mockMvc.perform(get("/api/v1/users/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + login.accessToken()
                        ))
                .andExpect(status().isOk())
                .andDo(document(
                        "account/me/success",
                        bearerRequest(),
                        documentedResponse(),
                        bearerTokenHeader(),
                        accountResponseFields()
                ));
    }

    @Test
    @DisplayName("본인 계정 조회의 Access JWT 누락 오류 계약")
    void documentsMissingAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpectAll(
                        status().isUnauthorized(),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                )
                .andDo(document(
                        "account/me/authentication-required",
                        plainRequest(),
                        documentedResponse(),
                        responseHeaders(
                                headerWithName(HttpHeaders.WWW_AUTHENTICATE)
                                        .description("Bearer Token 인증 challenge")
                        ),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("본인 이름 변경 성공 계약")
    void documentsCurrentAccountNameChange() throws Exception {
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                PASSWORD
        );

        api.changeName(login.accessToken(), "  새 이름  ")
                .andExpect(status().isNoContent())
                .andDo(document(
                        "account/me/update-name/success",
                        bearerRequest(),
                        documentedResponse(),
                        bearerTokenHeader(),
                        requestFields(
                                fieldWithPath("name")
                                        .description("앞뒤 공백을 제외한 1~30자 이름")
                        )
                ));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + login.accessToken()
                        ))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.name").value("새 이름")
                );
    }

    @Test
    @DisplayName("본인 이름 정책 위반 오류 계약")
    void documentsInvalidCurrentAccountName() throws Exception {
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                PASSWORD
        );

        api.changeName(login.accessToken(), "가".repeat(31))
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("ACCOUNT_INVALID_NAME")
                )
                .andDo(document(
                        "account/me/update-name/invalid-name",
                        bearerRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("본인 이름 변경의 Access JWT 누락 오류 계약")
    void documentsMissingAccessTokenForNameChange() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "새 이름"
                                }
                                """))
                .andExpectAll(
                        status().isUnauthorized(),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                );
    }

    @Test
    @DisplayName("Learning 계정 단건 조회 성공 계약")
    void documentsInternalAccount() throws Exception {
        Account account = saveAccount("active@example.com", "활성 사용자");

        mockMvc.perform(get(
                                "/api/v1/internal/accounts/{accountId}",
                                account.getId()
                        )
                        .with(learningCredential()))
                .andExpect(status().isOk())
                .andDo(document(
                        "internal/accounts/get/success",
                        learningRequest(),
                        documentedResponse(),
                        learningCredentialHeader(),
                        pathParameters(
                                parameterWithName("accountId")
                                        .description("조회할 계정 UUID")
                        ),
                        internalAccountResponseFields()
                ));
    }

    @Test
    @DisplayName("Learning 계정 단건 조회의 미존재 오류 계약")
    void documentsMissingInternalAccount() throws Exception {
        UUID missingAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000404"
        );

        mockMvc.perform(get(
                                "/api/v1/internal/accounts/{accountId}",
                                missingAccountId
                        )
                        .with(learningCredential()))
                .andExpectAll(
                        status().isNotFound(),
                        jsonPath("$.code").value("ACCOUNT_NOT_FOUND")
                )
                .andDo(document(
                        "internal/accounts/get/not-found",
                        learningRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("Learning 계정 일괄 조회 성공 계약")
    void documentsInternalAccountBatch() throws Exception {
        Account first = saveAccount("first@example.com", "첫 사용자");
        Account second = saveAccount("second@example.com", "두 번째 사용자");

        mockMvc.perform(post("/api/v1/internal/accounts/batch")
                        .with(learningCredential())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountIds": ["%s", "%s"]
                                }
                                """.formatted(first.getId(), second.getId())))
                .andExpect(status().isOk())
                .andDo(document(
                        "internal/accounts/batch/success",
                        learningRequest(),
                        documentedResponse(),
                        learningCredentialHeader(),
                        requestFields(
                                fieldWithPath("accountIds[]")
                                        .description("조회할 계정 UUID 목록. 최대 100개")
                        ),
                        responseFields(
                                fieldWithPath("[].accountId").description("존재하는 계정 UUID"),
                                fieldWithPath("[].displayName").description("표시 이름"),
                                fieldWithPath("[].status").description("계정 상태")
                        )
                ));
    }

    @Test
    @DisplayName("Learning 계정 일괄 조회의 빈 요청 오류 계약")
    void documentsInvalidInternalAccountBatch() throws Exception {
        mockMvc.perform(post("/api/v1/internal/accounts/batch")
                        .with(learningCredential())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountIds": []}
                                """))
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("COMMON_INVALID_REQUEST")
                )
                .andDo(document(
                        "internal/accounts/batch/invalid-request",
                        learningRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("Learning 후보 계정 검색 성공 계약")
    void documentsInternalAccountSearch() throws Exception {
        Account account = saveAccount("search@example.com", "검색 사용자");

        mockMvc.perform(post("/api/v1/internal/accounts/search")
                        .with(learningCredential())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "  검색  ",
                                  "candidateIds": ["%s"]
                                }
                                """.formatted(account.getId())))
                .andExpect(status().isOk())
                .andDo(document(
                        "internal/accounts/search/success",
                        learningRequest(),
                        documentedResponse(),
                        learningCredentialHeader(),
                        requestFields(
                                fieldWithPath("query").description(
                                        "이름 또는 이메일의 부분 검색어. 앞뒤 공백을 제거하며, "
                                                + "빈 문자열은 허용하지 않고 최대 100자"),
                                fieldWithPath("candidateIds[]").description(
                                        "Learning이 같은 기수 ACTIVE 멤버십과 현재 재실 기준으로 "
                                                + "확정한 후보 계정 UUID 목록")
                        ),
                        responseFields(
                                fieldWithPath("[].accountId").description("계정 UUID"),
                                fieldWithPath("[].displayName").description("표시 이름"),
                                fieldWithPath("[].email").description("이메일"),
                                fieldWithPath("[].status").description("계정 상태")
                        )
                ));
    }

    @Test
    @DisplayName("Learning Credential 누락 오류 계약")
    void documentsMissingLearningCredential() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/internal/accounts/{accountId}",
                        UUID.fromString("00000000-0000-0000-0000-000000000401")
                ))
                .andExpectAll(
                        status().isUnauthorized(),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                )
                .andDo(document(
                        "security/learning-authentication-required",
                        plainRequest(),
                        documentedResponse(),
                        responseHeaders(
                                headerWithName(HttpHeaders.WWW_AUTHENTICATE)
                                        .description("Learning HTTP Basic 인증 challenge")
                        ),
                        errorResponseFields()
                ));
    }

    private RequestHeadersSnippet frontendCredentialHeader() {
        return requestHeaders(
                headerWithName(HttpHeaders.AUTHORIZATION)
                        .description("Frontend Service HTTP Basic Credential")
        );
    }

    private RequestHeadersSnippet learningCredentialHeader() {
        return requestHeaders(
                headerWithName(HttpHeaders.AUTHORIZATION)
                        .description("Learning Service HTTP Basic Credential")
        );
    }

    private RequestHeadersSnippet bearerTokenHeader() {
        return requestHeaders(
                headerWithName(HttpHeaders.AUTHORIZATION)
                        .description("사용자의 Access JWT Bearer Token")
        );
    }

    private ResponseHeadersSnippet noStoreResponseHeader() {
        return responseHeaders(
                headerWithName(HttpHeaders.CACHE_CONTROL)
                        .description("Token 수명주기 응답의 저장을 막는 no-store")
        );
    }

    private RequestFieldsSnippet refreshTokenRequestFields() {
        return requestFields(
                fieldWithPath("refreshToken")
                        .description("Frontend Session에 저장한 불투명 Refresh Token")
        );
    }

    private ResponseFieldsSnippet accountResponseFields() {
        return responseFields(
                fieldWithPath("userId").description("계정 UUID"),
                fieldWithPath("email").description("정규화된 이메일"),
                fieldWithPath("name").description("표시 이름"),
                fieldWithPath("role").description("전역 역할"),
                fieldWithPath("status").description("계정 상태"),
                fieldWithPath("createdAt").description("계정 생성 시각"),
                fieldWithPath("updatedAt").description("마지막 변경 시각")
        );
    }

    private ResponseFieldsSnippet tokenResponseFields() {
        return responseFields(
                fieldWithPath("userId").description("계정 UUID"),
                fieldWithPath("globalRole").description("전역 역할"),
                fieldWithPath("accessToken").description("Access JWT"),
                fieldWithPath("accessTokenExpiresAt").description("Access JWT 만료 시각"),
                fieldWithPath("refreshToken").description("회전 가능한 불투명 Refresh Token"),
                fieldWithPath("refreshTokenExpiresAt").description("Refresh Token family 만료 시각")
        );
    }

    private ResponseFieldsSnippet internalAccountResponseFields() {
        return responseFields(
                fieldWithPath("accountId").description("계정 UUID"),
                fieldWithPath("displayName").description("표시 이름"),
                fieldWithPath("status").description("계정 상태")
        );
    }

    private ResponseFieldsSnippet errorResponseFields() {
        return responseFields(
                fieldWithPath("code").description("안정적인 오류 코드"),
                fieldWithPath("message").description("외부 공개 가능한 오류 메시지"),
                fieldWithPath("path").description("요청 경로"),
                fieldWithPath("requestId")
                        .optional()
                        .description("HTTP Request ID. 공통 추적 기능 도입 전에는 null")
        );
    }

    private RequestPostProcessor learningCredential() {
        return httpBasic(LEARNING_USERNAME, LEARNING_PASSWORD);
    }

    private OperationRequestPreprocessor frontendRequest() {
        return preprocessRequest(
                modifyHeaders().set(
                        HttpHeaders.AUTHORIZATION,
                        "Basic cmVkYWN0ZWQ6cmVkYWN0ZWQ="
                ),
                prettyPrint(),
                replacePattern(
                        PASSWORD_JSON,
                        "\"password\" : \"<password>\""
                ),
                replacePattern(
                        REFRESH_TOKEN_JSON,
                        "\"refreshToken\" : \"<refresh-token>\""
                )
        );
    }

    private OperationRequestPreprocessor bearerRequest() {
        return preprocessRequest(
                modifyHeaders().set(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer <access-token>"
                ),
                prettyPrint()
        );
    }

    private OperationRequestPreprocessor learningRequest() {
        return preprocessRequest(
                modifyHeaders().set(
                        HttpHeaders.AUTHORIZATION,
                        "Basic cmVkYWN0ZWQ6cmVkYWN0ZWQ="
                ),
                prettyPrint()
        );
    }

    private OperationRequestPreprocessor plainRequest() {
        return preprocessRequest(
                prettyPrint(),
                replacePattern(
                        PASSWORD_JSON,
                        "\"password\" : \"<password>\""
                )
        );
    }

    private OperationResponsePreprocessor documentedResponse() {
        return preprocessResponse(
                prettyPrint(),
                replacePattern(
                        ACCESS_TOKEN_JSON,
                        "\"accessToken\" : \"<access-token>\""
                ),
                replacePattern(
                        REFRESH_TOKEN_JSON,
                        "\"refreshToken\" : \"<refresh-token>\""
                )
        );
    }

    private Account saveAccount(String email, String name) {
        return accountJpaRepository.saveAndFlush(
                Account.register(email, "test-password-hash", name)
        );
    }
}
