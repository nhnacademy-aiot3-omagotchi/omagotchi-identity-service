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
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.auth.application.PasswordChangeV2Service;
import site.omagotchi.identityservice.auth.infrastructure.JwtAccessTokenIssuer;
import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;
import site.omagotchi.identityservice.email.application.EmailVerificationCooldownException;
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

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PasswordChangeV2Controller.class)
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
@DisplayName("v2 비밀번호 변경·이메일 OTP API")
class PasswordChangeV2ControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000635"
    );
    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";
    private static final String CHALLENGE_ID = "challenge-id";
    private static final String CODE = "123456";
    private static final String EMAIL_OTP_PATH = "/api/v2/users/me/password/email-otp";
    private static final Pattern CURRENT_PASSWORD_JSON = Pattern.compile(
            "\"currentPassword\"\\s*:\\s*\"[^\"]*\""
    );
    private static final Pattern NEW_PASSWORD_JSON = Pattern.compile(
            "\"newPassword\"\\s*:\\s*\"[^\"]*\""
    );
    private static final Pattern CODE_JSON = Pattern.compile(
            "\"code\"\\s*:\\s*\"[^\"]*\""
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private PasswordChangeV2Service passwordChangeService;

    @Test
    @DisplayName("현재 비밀번호와 새 비밀번호로 변경 요청")
    void changesPassword() throws Exception {
        mockMvc.perform(passwordChangeRequest())
                .andExpect(status().isNoContent())
                .andDo(document(
                        "v2/password-change/success",
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
                                        .description("현재 비밀번호와 다른 15~64자 새 비밀번호. 공백-only·제어 문자·UTF-8 72바이트 초과 입력은 허용하지 않음"),
                                fieldWithPath("challengeId")
                                        .description("PASSWORD_CHANGE OTP Challenge ID"),
                                fieldWithPath("code")
                                        .description("이메일로 받은 6자리 OTP")
                        )
                ));

        verify(passwordChangeService).changePassword(
                ACCOUNT_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD,
                CHALLENGE_ID,
                CODE
        );
    }

    @Test
    @DisplayName("JWT 계정으로 비밀번호 변경 이메일 OTP 발급·발송 요청")
    void requestsPasswordChangeEmailOtp() throws Exception {
        given(passwordChangeService.requestEmailOtp(ACCOUNT_ID))
                .willReturn(new EmailVerificationChallengeResult("challenge-id", 600L));

        mockMvc.perform(emailOtpRequest())
                .andExpectAll(
                        status().isAccepted(),
                        header().string(HttpHeaders.CACHE_CONTROL, "no-store"),
                        jsonPath("$.challengeId").value("challenge-id"),
                        jsonPath("$.expiresInSeconds").value(600L)
                )
                .andDo(document(
                        "v2/password-change/email-otp/success",
                        documentedRequest(),
                        documentedResponse(),
                        requestHeaders(
                                headerWithName(HttpHeaders.AUTHORIZATION)
                                        .description("사용자의 Access JWT Bearer Token")
                        ),
                        responseHeaders(
                                headerWithName(HttpHeaders.CACHE_CONTROL)
                                        .description("OTP 요청 응답의 저장을 막는 no-store")
                        ),
                        responseFields(
                                fieldWithPath("challengeId")
                                        .description("최종 비밀번호 변경 요청에 OTP와 함께 제출할 식별자"),
                                fieldWithPath("expiresInSeconds").description("OTP 유효 시간(초)")
                        )
                ));

        verify(passwordChangeService).requestEmailOtp(ACCOUNT_ID);
    }

    @Test
    @DisplayName("Access JWT 없는 비밀번호 변경 OTP 요청 거부")
    void rejectsEmailOtpRequestWithoutBearerAuthentication() throws Exception {
        mockMvc.perform(post(EMAIL_OTP_PATH))
                .andExpectAll(
                        status().isUnauthorized(),
                        header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                );

        verifyNoInteractions(passwordChangeService);
    }

    @Test
    @DisplayName("Frontend Basic 인증으로 비밀번호 변경 OTP를 요청할 수 없음")
    void rejectsBasicAuthenticationForEmailOtpRequest() throws Exception {
        mockMvc.perform(post(EMAIL_OTP_PATH)
                        .with(httpBasic("frontend", "test-only-frontend-credential-password")))
                .andExpectAll(
                        status().isUnauthorized(),
                        header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer"))
                );

        verifyNoInteractions(passwordChangeService);
    }

    @Test
    @DisplayName("이전 비밀번호 변경 Challenge 경로는 더 이상 매핑하지 않음")
    void rejectsLegacyEmailChallengePath() throws Exception {
        mockMvc.perform(post("/api/v2/users/me/password/email-verification/challenges")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(passwordChangeService);
    }

    @Test
    @DisplayName("OTP 재발급 쿨다운의 Retry-After 계약 유지")
    void preservesEmailOtpCooldownResponse() throws Exception {
        given(passwordChangeService.requestEmailOtp(ACCOUNT_ID))
                .willThrow(new EmailVerificationCooldownException(30L));

        mockMvc.perform(emailOtpRequest())
                .andExpectAll(
                        status().isTooManyRequests(),
                        header().string(HttpHeaders.RETRY_AFTER, "30"),
                        jsonPath("$.code").value("EMAIL_VERIFICATION_COOLDOWN_ACTIVE"),
                        jsonPath("$.path").value(EMAIL_OTP_PATH)
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
                        "v2/password-change/authentication-required",
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
                .changePassword(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        NEW_PASSWORD,
                        CHALLENGE_ID,
                        CODE
                );

        mockMvc.perform(passwordChangeRequest())
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("ACCOUNT_CURRENT_PASSWORD_MISMATCH")
                )
                .andDo(document(
                        "v2/password-change/current-password-mismatch",
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
                .changePassword(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        NEW_PASSWORD,
                        CHALLENGE_ID,
                        CODE
                );

        mockMvc.perform(passwordChangeRequest())
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("ACCOUNT_INVALID_PASSWORD")
                )
                .andDo(document(
                        "v2/password-change/invalid-new-password",
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
                .changePassword(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        NEW_PASSWORD,
                        CHALLENGE_ID,
                        CODE
                );

        mockMvc.perform(passwordChangeRequest())
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("ACCOUNT_PASSWORD_UNCHANGED")
                )
                .andDo(document(
                        "v2/password-change/unchanged-password",
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
                .changePassword(
                        ACCOUNT_ID,
                        CURRENT_PASSWORD,
                        NEW_PASSWORD,
                        CHALLENGE_ID,
                        CODE
                );

        mockMvc.perform(passwordChangeRequest())
                .andExpectAll(
                        status().isForbidden(),
                        jsonPath("$.code").value("ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED")
                )
                .andDo(document(
                        "v2/password-change/not-allowed",
                        documentedRequest(),
                        documentedResponse(),
                        errorResponseFields()
                ));
    }

    private MockHttpServletRequestBuilder emailOtpRequest() {
        return post(EMAIL_OTP_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken());
    }

    private MockHttpServletRequestBuilder passwordChangeRequest() {
        return passwordChangeRequestWithoutAuthentication()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken());
    }

    private MockHttpServletRequestBuilder passwordChangeRequestWithoutAuthentication() {
        return patch("/api/v2/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "currentPassword": "password-passphrase",
                          "newPassword": "new-password-passphrase",
                          "challengeId": "challenge-id",
                          "code": "123456"
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

    private ResponseFieldsSnippet errorResponseFields() {
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
                ),
                replacePattern(
                        CODE_JSON,
                        "\"code\" : \"<verification-code>\""
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
                ),
                replacePattern(
                        CODE_JSON,
                        "\"code\" : \"<verification-code>\""
                )
        );
    }
}
