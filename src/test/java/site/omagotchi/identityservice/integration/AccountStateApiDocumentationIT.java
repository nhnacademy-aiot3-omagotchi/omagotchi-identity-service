package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.RequestHeadersSnippet;
import org.springframework.restdocs.headers.ResponseHeadersSnippet;
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor;
import org.springframework.restdocs.operation.preprocess.OperationResponsePreprocessor;
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.accountstate.infrastructure.AccountStatusChangeAuditJpaRepository;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
@Import({TestcontainersConfig.class, TestJwtConfig.class})
@DisplayName("계정 상태 변경 API 문서")
class AccountStateApiDocumentationIT {

    private static final String PASSWORD = "password-passphrase";
    private static final Pattern CURRENT_PASSWORD_JSON = Pattern.compile(
            "\"currentPassword\"\\s*:\\s*\"[^\"]*\""
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private AccountStatusChangeAuditJpaRepository auditJpaRepository;

    private AuthApiTestClient api;
    private AccountStateTestFixture fixture;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        fixture = new AccountStateTestFixture(jdbcTemplate);
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("본인 탈퇴 성공 계약")
    void documentsSelfWithdrawal() throws Exception {
        // Given
        api.signupSuccessfully("withdraw-docs@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "withdraw-docs@example.com",
                PASSWORD
        );

        // When
        ResultActions response = api.withdraw(login.accessToken(), PASSWORD);

        // Then
        response
                .andExpect(status().isNoContent())
                .andDo(document(
                        "account/withdrawal/success",
                        sensitiveBearerRequest(),
                        documentedResponse(),
                        bearerTokenHeader(),
                        noStoreResponseHeader(),
                        requestFields(
                                fieldWithPath("currentPassword")
                                        .description("탈퇴 의사를 재확인할 현재 비밀번호")
                        )
                ));
    }

    @Test
    @DisplayName("본인 탈퇴의 현재 비밀번호 불일치 계약")
    void documentsWithdrawalPasswordMismatch() throws Exception {
        // Given
        api.signupSuccessfully("withdraw-mismatch-docs@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "withdraw-mismatch-docs@example.com",
                PASSWORD
        );

        // When
        ResultActions response = api.withdraw(
                login.accessToken(),
                "wrong-password-passphrase"
        );

        // Then
        response
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("ACCOUNT_CURRENT_PASSWORD_MISMATCH")
                )
                .andDo(document(
                        "account/withdrawal/current-password-mismatch",
                        sensitiveBearerRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("SYSTEM_ADMIN 계정 비활성화 성공 계약")
    void documentsAdministrativeDisable() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "admin-docs@example.com"
        );
        UUID targetAccountId = api.signupSuccessfully("disable-target-docs@example.com");

        // When
        ResultActions response = api.changeAccountStatus(
                        administrator.accessToken(),
                        targetAccountId,
                        "DISABLED",
                        "보안 사고 대응"
                );

        // Then
        response
                .andExpect(status().isNoContent())
                .andDo(document(
                        "admin/accounts/status/disable-success",
                        bearerRequest(),
                        documentedResponse(),
                        bearerTokenHeader(),
                        noStoreResponseHeader(),
                        pathParameters(
                                parameterWithName("user-id")
                                        .description("상태를 변경할 계정 UUID")
                        ),
                        requestFields(
                                fieldWithPath("status")
                                        .description("목표 상태. ACTIVE 또는 DISABLED"),
                                fieldWithPath("reason")
                                        .description("앞뒤 공백을 제외한 1~500자 변경 사유. NUL 제외")
                        )
                ));
    }

    @Test
    @DisplayName("허용되지 않은 관리자 상태 전이 계약")
    void documentsRejectedAdministrativeTransition() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "transition-admin-docs@example.com"
        );
        UUID targetAccountId = api.signupSuccessfully("withdrawn-target-docs@example.com");
        fixture.changeStatus(targetAccountId, AccountStatus.WITHDRAWN);

        // When
        ResultActions response = api.changeAccountStatus(
                        administrator.accessToken(),
                        targetAccountId,
                        "ACTIVE",
                        "복구 시도"
                );

        // Then
        response
                .andExpectAll(
                        status().isConflict(),
                        jsonPath("$.code")
                                .value("ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED")
                )
                .andDo(document(
                        "admin/accounts/status/transition-not-allowed",
                        bearerRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("필수 상태 변경 사유 오류 계약")
    void documentsInvalidAdministrativeReason() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "reason-admin-docs@example.com"
        );
        UUID targetAccountId = api.signupSuccessfully("reason-target-docs@example.com");

        // When
        ResultActions response = api.changeAccountStatus(
                        administrator.accessToken(),
                        targetAccountId,
                        "DISABLED",
                        "   "
                );

        // Then
        response
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code")
                                .value("ACCOUNT_STATUS_CHANGE_INVALID_REASON")
                )
                .andDo(document(
                        "admin/accounts/status/invalid-reason",
                        bearerRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("존재하지 않는 대상 계정 오류 계약")
    void documentsMissingTargetAccount() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "missing-target-admin-docs@example.com"
        );

        // When
        ResultActions response = api.changeAccountStatus(
                        administrator.accessToken(),
                        UUID.fromString("00000000-0000-0000-0000-000000000404"),
                        "DISABLED",
                        "존재하지 않는 대상"
                );

        // Then
        response
                .andExpectAll(
                        status().isNotFound(),
                        jsonPath("$.code").value("ACCOUNT_NOT_FOUND")
                )
                .andDo(document(
                        "admin/accounts/status/account-not-found",
                        bearerRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("허용하지 않는 목표 상태의 요청 본문 오류 계약")
    void documentsMalformedTargetStatus() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "malformed-status-admin-docs@example.com"
        );
        UUID targetAccountId = api.signupSuccessfully(
                "malformed-status-target-docs@example.com"
        );

        // When
        ResultActions response = api.changeAccountStatus(
                        administrator.accessToken(),
                        targetAccountId,
                        "LOCKED",
                        "허용하지 않는 목표 상태"
                );

        // Then
        response
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("COMMON_MALFORMED_REQUEST")
                )
                .andDo(document(
                        "admin/accounts/status/malformed-target-status",
                        bearerRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    private AuthApiTestClient.TokenBundle createAdministrator(String email) throws Exception {
        UUID accountId = api.signupSuccessfully(email);
        fixture.changeGlobalRole(accountId, GlobalRole.SYSTEM_ADMIN);
        return api.loginSuccessfully(email, PASSWORD);
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
                        .description("상태 변경 응답의 저장을 막는 no-store")
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

    private OperationRequestPreprocessor sensitiveBearerRequest() {
        return preprocessRequest(
                modifyHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer <access-token>"),
                prettyPrint(),
                replacePattern(
                        CURRENT_PASSWORD_JSON,
                        "\"currentPassword\" : \"<current-password>\""
                )
        );
    }

    private OperationRequestPreprocessor bearerRequest() {
        return preprocessRequest(
                modifyHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer <access-token>"),
                prettyPrint()
        );
    }

    private OperationResponsePreprocessor documentedResponse() {
        return preprocessResponse(prettyPrint());
    }

    private void cleanDatabase() {
        auditJpaRepository.deleteAll();
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }
}
