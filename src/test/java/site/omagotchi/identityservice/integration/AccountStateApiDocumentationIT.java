package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.headers.RequestHeadersSnippet;
import org.springframework.restdocs.headers.ResponseHeadersSnippet;
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor;
import org.springframework.restdocs.operation.preprocess.OperationResponsePreprocessor;
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.accountstate.application.AccountStateErrorCode;
import site.omagotchi.identityservice.accountstate.application.AdminAccountStatus;
import site.omagotchi.identityservice.accountstate.application.AdminAccountStatusChangeService;
import site.omagotchi.identityservice.accountstate.application.SelfAccountWithdrawalService;
import site.omagotchi.identityservice.accountstate.presentation.AdminAccountStatusController;
import site.omagotchi.identityservice.accountstate.presentation.SelfAccountWithdrawalController;
import site.omagotchi.identityservice.auth.infrastructure.JwtAccessTokenIssuer;
import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.security.error.SecurityErrorResponseHandler;
import site.omagotchi.identityservice.global.security.jwt.JwtAuthorityConfig;
import site.omagotchi.identityservice.global.security.jwt.JwtConfig;
import site.omagotchi.identityservice.global.security.jwt.JwtProperties;
import site.omagotchi.identityservice.global.security.jwt.JwtSecurityConfig;

import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.headers.HeaderDocumentation.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        SelfAccountWithdrawalController.class,
        AdminAccountStatusController.class
})
@Import({
        JwtSecurityConfig.class,
        JwtConfig.class,
        JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class,
        TestJwtConfig.class
})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
@DisplayName("계정 상태 변경 API 문서")
class AccountStateApiDocumentationIT {

    private static final String PASSWORD = "password-passphrase";
    private static final Pattern CURRENT_PASSWORD_JSON = Pattern.compile(
            "\"currentPassword\"\\s*:\\s*\"[^\"]*\""
    );
    private static final UUID USER_ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000101"
    );
    private static final UUID ADMIN_ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000102"
    );
    private static final UUID TARGET_ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000103"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private SelfAccountWithdrawalService selfAccountWithdrawalService;

    @MockitoBean
    private AdminAccountStatusChangeService accountStatusChangeService;

    @Test
    @DisplayName("본인 탈퇴 성공 계약")
    void documentsSelfWithdrawal() throws Exception {
        // Given
        String token = userAccessToken();

        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawalBody(PASSWORD)))
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

        verify(selfAccountWithdrawalService).withdraw(USER_ACCOUNT_ID, PASSWORD);
    }

    @Test
    @DisplayName("본인 탈퇴의 현재 비밀번호 불일치 계약")
    void documentsWithdrawalPasswordMismatch() throws Exception {
        // Given
        String token = userAccessToken();
        willThrow(new BusinessException(AccountErrorCode.CURRENT_PASSWORD_MISMATCH))
                .given(selfAccountWithdrawalService)
                .withdraw(USER_ACCOUNT_ID, "wrong-password-passphrase");

        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawalBody("wrong-password-passphrase")))
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
        String token = adminAccessToken();

        // When & Then
        mockMvc.perform(patch(
                        "/api/v1/admin/accounts/{user-id}/status",
                        TARGET_ACCOUNT_ID
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountStatusBody("DISABLED", "보안 사고 대응")))
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

        verify(accountStatusChangeService).changeStatus(
                ADMIN_ACCOUNT_ID,
                TARGET_ACCOUNT_ID,
                AdminAccountStatus.DISABLED,
                "보안 사고 대응"
        );
    }

    @Test
    @DisplayName("허용되지 않은 관리자 상태 전이 계약")
    void documentsRejectedAdministrativeTransition() throws Exception {
        // Given
        String token = adminAccessToken();
        willThrow(new BusinessException(AccountErrorCode.STATUS_TRANSITION_NOT_ALLOWED))
                .given(accountStatusChangeService)
                .changeStatus(ADMIN_ACCOUNT_ID, TARGET_ACCOUNT_ID, AdminAccountStatus.ACTIVE, "복구 시도");

        // When & Then
        mockMvc.perform(patch(
                        "/api/v1/admin/accounts/{user-id}/status",
                        TARGET_ACCOUNT_ID
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountStatusBody("ACTIVE", "복구 시도")))
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
        String token = adminAccessToken();
        willThrow(new BusinessException(AccountStateErrorCode.INVALID_REASON))
                .given(accountStatusChangeService)
                .changeStatus(ADMIN_ACCOUNT_ID, TARGET_ACCOUNT_ID, AdminAccountStatus.DISABLED, "   ");

        // When & Then
        mockMvc.perform(patch(
                        "/api/v1/admin/accounts/{user-id}/status",
                        TARGET_ACCOUNT_ID
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountStatusBody("DISABLED", "   ")))
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
        String token = adminAccessToken();
        UUID missingAccountId = UUID.fromString("00000000-0000-0000-0000-000000000404");
        willThrow(new BusinessException(AccountErrorCode.NOT_FOUND))
                .given(accountStatusChangeService)
                .changeStatus(ADMIN_ACCOUNT_ID, missingAccountId, AdminAccountStatus.DISABLED, "존재하지 않는 대상");

        // When & Then
        mockMvc.perform(patch(
                        "/api/v1/admin/accounts/{user-id}/status",
                        missingAccountId
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountStatusBody("DISABLED", "존재하지 않는 대상")))
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
        String token = adminAccessToken();

        // When & Then
        mockMvc.perform(patch(
                        "/api/v1/admin/accounts/{user-id}/status",
                        TARGET_ACCOUNT_ID
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountStatusBody("LOCKED", "허용하지 않는 목표 상태")))
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

    private String userAccessToken() {
        return new JwtAccessTokenIssuer(
                jwtEncoder,
                jwtProperties,
                Clock.systemUTC()
        ).issue(USER_ACCOUNT_ID, "USER").value();
    }

    private String adminAccessToken() {
        return new JwtAccessTokenIssuer(
                jwtEncoder,
                jwtProperties,
                Clock.systemUTC()
        ).issue(ADMIN_ACCOUNT_ID, "SYSTEM_ADMIN").value();
    }

    private String withdrawalBody(String currentPassword) {
        return """
                {
                  "currentPassword": "%s"
                }
                """.formatted(currentPassword);
    }

    private String accountStatusBody(String status, String reason) {
        return """
                {
                  "status": "%s",
                  "reason": "%s"
                }
                """.formatted(status, reason);
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
}
