package site.omagotchi.identityservice.auth.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor;
import org.springframework.restdocs.operation.preprocess.OperationResponsePreprocessor;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.auth.application.PasswordChangeService;
import site.omagotchi.identityservice.auth.infrastructure.JwtAccessTokenIssuer;
import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.security.error.SecurityErrorResponseHandler;
import site.omagotchi.identityservice.global.security.jwt.JwtAuthorityConfig;
import site.omagotchi.identityservice.global.security.jwt.JwtConfig;
import site.omagotchi.identityservice.global.security.jwt.JwtProperties;
import site.omagotchi.identityservice.global.security.jwt.JwtSecurityConfig;
import site.omagotchi.identityservice.integration.TestJwtConfig;

import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PasswordChangeController.class)
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
@DisplayName("비밀번호 변경 API")
class PasswordChangeControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000635"
    );
    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";
    private static final Pattern CURRENT_PASSWORD_JSON = Pattern.compile(
            "\"currentPassword\"\\s*:\\s*\"[^\"]*\""
    );
    private static final Pattern NEW_PASSWORD_JSON = Pattern.compile(
            "\"newPassword\"\\s*:\\s*\"[^\"]*\""
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private PasswordChangeService passwordChangeService;

    @Test
    @DisplayName("현재 비밀번호와 새 비밀번호로 변경 요청")
    void changesPassword() throws Exception {
        mockMvc.perform(passwordChangeRequest())
                .andExpect(status().isNoContent())
                .andDo(document(
                        "password-change/success",
                        documentedRequest(),
                        documentedResponse(),
                        requestHeaders(
                                headerWithName(HttpHeaders.AUTHORIZATION)
                                        .description("사용자의 Access JWT Bearer Token")
                        ),
                        responseHeaders(
                                headerWithName(HttpHeaders.CACHE_CONTROL)
                                        .description("민감 작업 응답의 저장을 막는 no-store")
                        ),
                        requestFields(
                                fieldWithPath("currentPassword")
                                        .description("현재 비밀번호"),
                                fieldWithPath("newPassword")
                                        .description("현재 비밀번호와 다른 15~64자 새 비밀번호. 공백-only·제어 문자·UTF-8 72바이트 초과 입력은 허용하지 않음")
                        )
                ));

        verify(passwordChangeService).changePassword(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );
    }

    @Test
    @DisplayName("Access JWT 없는 요청의 인증 오류 계약")
    void documentsAuthenticationRequired() throws Exception {
        mockMvc.perform(passwordChangeRequestWithoutAuthentication())
                .andExpectAll(
                        status().isUnauthorized(),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                )
                .andDo(document(
                        "password-change/authentication-required",
                        plainRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("현재 비밀번호 불일치 오류 계약")
    void documentsCurrentPasswordMismatch() throws Exception {
        willThrow(new BusinessException(AccountErrorCode.CURRENT_PASSWORD_MISMATCH))
                .given(passwordChangeService)
                .changePassword(ACCOUNT_ID, CURRENT_PASSWORD, NEW_PASSWORD);

        mockMvc.perform(passwordChangeRequest())
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("ACCOUNT_CURRENT_PASSWORD_MISMATCH")
                )
                .andDo(document(
                        "password-change/current-password-mismatch",
                        documentedRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("새 비밀번호 정책 위반 오류 계약")
    void documentsInvalidNewPassword() throws Exception {
        willThrow(new BusinessException(AccountErrorCode.INVALID_PASSWORD))
                .given(passwordChangeService)
                .changePassword(ACCOUNT_ID, CURRENT_PASSWORD, NEW_PASSWORD);

        mockMvc.perform(passwordChangeRequest())
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("ACCOUNT_INVALID_PASSWORD")
                )
                .andDo(document(
                        "password-change/invalid-new-password",
                        documentedRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("현재 비밀번호 재사용 오류 계약")
    void documentsUnchangedPassword() throws Exception {
        willThrow(new BusinessException(AccountErrorCode.PASSWORD_UNCHANGED))
                .given(passwordChangeService)
                .changePassword(ACCOUNT_ID, CURRENT_PASSWORD, NEW_PASSWORD);

        mockMvc.perform(passwordChangeRequest())
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("ACCOUNT_PASSWORD_UNCHANGED")
                )
                .andDo(document(
                        "password-change/unchanged-password",
                        documentedRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    @Test
    @DisplayName("비밀번호 변경 불가 계정 오류 계약")
    void documentsUnavailableAccount() throws Exception {
        willThrow(new BusinessException(AccountErrorCode.PASSWORD_CHANGE_NOT_ALLOWED))
                .given(passwordChangeService)
                .changePassword(ACCOUNT_ID, CURRENT_PASSWORD, NEW_PASSWORD);

        mockMvc.perform(passwordChangeRequest())
                .andExpectAll(
                        status().isForbidden(),
                        jsonPath("$.code").value("ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED")
                )
                .andDo(document(
                        "password-change/not-allowed",
                        documentedRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    passwordChangeRequest() {
        return passwordChangeRequestWithoutAuthentication()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    passwordChangeRequestWithoutAuthentication() {
        return patch("/api/v1/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "currentPassword": "password-passphrase",
                          "newPassword": "new-password-passphrase"
                        }
                        """);
    }

    private String accessToken() {
        return new JwtAccessTokenIssuer(
                jwtEncoder,
                jwtProperties,
                Clock.systemUTC()
        ).issue(ACCOUNT_ID, "USER").value();
    }

    private org.springframework.restdocs.payload.ResponseFieldsSnippet errorResponseFields() {
        return responseFields(
                fieldWithPath("code").description("안정적인 오류 코드"),
                fieldWithPath("message").description("사용자에게 공개 가능한 오류 메시지"),
                fieldWithPath("path").description("요청 경로"),
                fieldWithPath("requestId")
                        .optional()
                        .description("HTTP Request ID. 공통 추적 기능 도입 전에는 null")
        );
    }

    private OperationRequestPreprocessor documentedRequest() {
        return preprocessRequest(
                modifyHeaders().set(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer <access-token>"
                ),
                prettyPrint(),
                replacePattern(
                        CURRENT_PASSWORD_JSON,
                        "\"currentPassword\" : \"<password>\""
                ),
                replacePattern(
                        NEW_PASSWORD_JSON,
                        "\"newPassword\" : \"<password>\""
                )
        );
    }

    private OperationResponsePreprocessor documentedResponse() {
        return preprocessResponse(prettyPrint());
    }

    private OperationRequestPreprocessor plainRequest() {
        return preprocessRequest(
                prettyPrint(),
                replacePattern(
                        CURRENT_PASSWORD_JSON,
                        "\"currentPassword\" : \"<password>\""
                ),
                replacePattern(
                        NEW_PASSWORD_JSON,
                        "\"newPassword\" : \"<password>\""
                )
        );
    }
}
