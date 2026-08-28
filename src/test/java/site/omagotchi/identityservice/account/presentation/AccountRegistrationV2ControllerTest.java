package site.omagotchi.identityservice.account.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import site.omagotchi.identityservice.account.application.AccountRegistrationV2Service;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;
import site.omagotchi.identityservice.email.application.EmailVerificationCooldownException;
import site.omagotchi.identityservice.global.config.PasswordEncoderConfig;
import site.omagotchi.identityservice.global.security.basic.ServiceCredentialAuthenticationProviderFactory;
import site.omagotchi.identityservice.global.security.error.SecurityErrorResponseHandler;
import site.omagotchi.identityservice.global.security.frontend.FrontendCredentialProperties;
import site.omagotchi.identityservice.global.security.frontend.FrontendSecurityConfig;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountRegistrationV2Controller.class)
@Import({
        FrontendSecurityConfig.class,
        ServiceCredentialAuthenticationProviderFactory.class,
        PasswordEncoderConfig.class,
        SecurityErrorResponseHandler.class
})
@EnableConfigurationProperties(FrontendCredentialProperties.class)
@ActiveProfiles("test")
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
@DisplayName("v2 회원가입·이메일 OTP API")
class AccountRegistrationV2ControllerTest {

    private static final String EMAIL_OTP_PATH = "/api/v2/auth/signup/email-otp";
    private static final Pattern PASSWORD_JSON = Pattern.compile(
            "\"password\"\\s*:\\s*\"[^\"]*\""
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FrontendCredentialProperties frontendProperties;

    @MockitoBean
    private AccountRegistrationV2Service accountRegistrationService;

    @Test
    @DisplayName("v2 최종 가입에서 Challenge ID와 OTP를 전달")
    void signsUpWithEmailOtp() throws Exception {
        Account account = mock(Account.class);
        given(account.getId()).willReturn(UUID.fromString("00000000-0000-0000-0000-000000000635"));
        given(account.getEmail()).willReturn("user@example.com");
        given(account.getName()).willReturn("사용자");
        given(account.getGlobalRole()).willReturn(GlobalRole.USER);
        given(account.getStatus()).willReturn(AccountStatus.ACTIVE);
        given(account.getCreatedAt()).willReturn(Instant.parse("2026-08-28T00:00:00Z"));
        given(account.getUpdatedAt()).willReturn(Instant.parse("2026-08-28T00:00:00Z"));
        given(accountRegistrationService.signUp(
                "user@example.com", "password-passphrase", "사용자", "challenge-id", "123456"
        )).willReturn(account);

        mockMvc.perform(post("/api/v2/auth/signup")
                        .with(httpBasic(frontendProperties.username(), frontendProperties.password()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password-passphrase",
                                  "name": "사용자",
                                  "challengeId": "challenge-id",
                                  "code": "123456"
                                }
                                """))
                .andExpectAll(
                        status().isCreated(),
                        jsonPath("$.email").value("user@example.com")
                )
                .andDo(document(
                        "v2/auth/signup/success",
                        preprocessRequest(
                                modifyHeaders().set(HttpHeaders.AUTHORIZATION, "Basic cmVkYWN0ZWQ6cmVkYWN0ZWQ="),
                                prettyPrint(),
                                replacePattern(PASSWORD_JSON, "\"password\" : \"<password>\""),
                                replacePattern(
                                        Pattern.compile("\"code\"\\s*:\\s*\"[^\"]*\""),
                                        "\"code\" : \"<verification-code>\""
                                )
                        ),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION)
                                .description("Frontend Service HTTP Basic Credential")),
                        requestFields(
                                fieldWithPath("email").description("가입할 이메일"),
                                fieldWithPath("password").description("가입 비밀번호"),
                                fieldWithPath("name").description("표시 이름"),
                                fieldWithPath("challengeId").description("SIGN_UP OTP Challenge ID"),
                                fieldWithPath("code").description("이메일로 받은 6자리 OTP")
                        )
                ));

        verify(accountRegistrationService).signUp(
                "user@example.com", "password-passphrase", "사용자", "challenge-id", "123456"
        );
    }

    @Test
    @DisplayName("Frontend 인증 후 SIGN_UP 이메일 OTP 발급·발송 요청")
    void requestsSignUpEmailOtp() throws Exception {
        given(accountRegistrationService.requestEmailOtp(
                "user@example.com",
                "password-passphrase",
                "사용자"
        )).willReturn(new EmailVerificationChallengeResult("challenge-id", 600L));

        mockMvc.perform(authenticatedEmailOtpRequest())
                .andExpectAll(
                        status().isAccepted(),
                        header().string(HttpHeaders.CACHE_CONTROL, "no-store"),
                        jsonPath("$.challengeId").value("challenge-id"),
                        jsonPath("$.expiresInSeconds").value(600L)
                )
                .andDo(document(
                        "v2/auth/signup/email-otp/success",
                        preprocessRequest(
                                modifyHeaders().set(
                                        HttpHeaders.AUTHORIZATION,
                                        "Basic cmVkYWN0ZWQ6cmVkYWN0ZWQ="
                                ),
                                prettyPrint(),
                                replacePattern(PASSWORD_JSON, "\"password\" : \"<password>\"")
                        ),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                                headerWithName(HttpHeaders.AUTHORIZATION)
                                        .description("Frontend Service HTTP Basic Credential")
                        ),
                        requestFields(
                                fieldWithPath("email").description("가입할 이메일"),
                                fieldWithPath("password").description("가입 정책 사전 검사용 비밀번호"),
                                fieldWithPath("name").description("가입 정책 사전 검사용 이름")
                        ),
                        responseHeaders(
                                headerWithName(HttpHeaders.CACHE_CONTROL)
                                        .description("OTP 요청 응답의 저장을 막는 no-store")
                        ),
                        responseFields(
                                fieldWithPath("challengeId")
                                        .description("최종 회원가입 요청에 OTP와 함께 제출할 식별자"),
                                fieldWithPath("expiresInSeconds").description("OTP 유효 시간(초)")
                        )
                ));

        verify(accountRegistrationService).requestEmailOtp(
                "user@example.com",
                "password-passphrase",
                "사용자"
        );
    }

    @Test
    @DisplayName("Frontend 인증 없는 OTP 요청 거부")
    void rejectsEmailOtpRequestWithoutFrontendAuthentication() throws Exception {
        mockMvc.perform(emailOtpRequest())
                .andExpectAll(
                        status().isUnauthorized(),
                        header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Basic")),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                );

        verifyNoInteractions(accountRegistrationService);
    }

    @Test
    @DisplayName("사용자 Bearer 인증으로 가입 OTP를 요청할 수 없음")
    void rejectsBearerAuthenticationForEmailOtpRequest() throws Exception {
        mockMvc.perform(emailOtpRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-frontend-credential"))
                .andExpectAll(
                        status().isUnauthorized(),
                        header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Basic"))
                );

        verifyNoInteractions(accountRegistrationService);
    }

    @Test
    @DisplayName("이전 이메일 인증 Challenge 경로는 더 이상 매핑하지 않음")
    void rejectsLegacyEmailChallengePath() throws Exception {
        mockMvc.perform(post("/api/v2/auth/signup/email-verification/challenges")
                        .with(httpBasic(frontendProperties.username(), frontendProperties.password())))
                .andExpect(status().isNotFound());

        verifyNoInteractions(accountRegistrationService);
    }

    @Test
    @DisplayName("가입 입력이 누락된 OTP 요청 거부")
    void rejectsEmailOtpRequestWithoutSignupFields() throws Exception {
        mockMvc.perform(authenticatedEmailOtpRequest()
                        .content("""
                                {"email": "user@example.com"}
                                """))
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("COMMON_INVALID_REQUEST")
                );

        verifyNoInteractions(accountRegistrationService);
    }

    @Test
    @DisplayName("OTP 재발급 쿨다운의 Retry-After 계약 유지")
    void preservesEmailOtpCooldownResponse() throws Exception {
        given(accountRegistrationService.requestEmailOtp(
                "user@example.com",
                "password-passphrase",
                "사용자"
        )).willThrow(new EmailVerificationCooldownException(30L));

        mockMvc.perform(authenticatedEmailOtpRequest())
                .andExpectAll(
                        status().isTooManyRequests(),
                        header().string(HttpHeaders.RETRY_AFTER, "30"),
                        jsonPath("$.code").value("EMAIL_VERIFICATION_COOLDOWN_ACTIVE"),
                        jsonPath("$.path").value(EMAIL_OTP_PATH)
                );
    }

    private MockHttpServletRequestBuilder authenticatedEmailOtpRequest() {
        return emailOtpRequest()
                .with(httpBasic(frontendProperties.username(), frontendProperties.password()));
    }

    private MockHttpServletRequestBuilder emailOtpRequest() {
        return post(EMAIL_OTP_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "user@example.com",
                          "password": "password-passphrase",
                          "name": "사용자"
                        }
                        """);
    }
}
