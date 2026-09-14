package site.omagotchi.identityservice.account.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import site.omagotchi.identityservice.account.application.AccountRegistrationV2Service;
import site.omagotchi.identityservice.account.application.result.SignupEmailOtpResult;
import site.omagotchi.identityservice.account.presentation.request.SignupEmailOtpRequest;
import site.omagotchi.identityservice.account.presentation.response.SignupEmailOtpResponse;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationCooldownException;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationErrorCode;
import site.omagotchi.identityservice.global.config.PasswordEncoderConfig;
import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.identityservice.global.security.basic.ServiceCredentialAuthenticationProviderFactory;
import site.omagotchi.identityservice.global.security.error.SecurityErrorResponseHandler;
import site.omagotchi.identityservice.global.security.frontend.FrontendCredentialProperties;
import site.omagotchi.identityservice.global.security.frontend.FrontendSecurityConfig;
import site.omagotchi.identityservice.global.security.jwt.JwtAuthorityConfig;
import site.omagotchi.identityservice.global.security.jwt.JwtConfig;
import site.omagotchi.identityservice.global.security.jwt.JwtProperties;
import site.omagotchi.identityservice.global.security.jwt.JwtSecurityConfig;
import site.omagotchi.identityservice.integration.TestJwtConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.headers.HeaderDocumentation.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SignupEmailOtpController.class)
@Import({PasswordEncoderConfig.class, FrontendSecurityConfig.class, ServiceCredentialAuthenticationProviderFactory.class,
        JwtSecurityConfig.class, JwtConfig.class, JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class, TestJwtConfig.class})
@EnableConfigurationProperties({JwtProperties.class, FrontendCredentialProperties.class})
@ActiveProfiles("test")
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
class SignupEmailOtpControllerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000700201");
    private static final String EMAIL = "member@example.com";
    private static final String PASSWORD = "long-enough-password";
    private static final String SIGNUP = """
            {"email":"member@example.com","password":"long-enough-password","name":"member"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AccountRegistrationV2Service signup;
    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700202"
    );

    @Test
    @DisplayName("회원가입 OTP 발급은 202와 no-store 응답")
    void returnsAcceptedWithoutCaching() {
        // Given
        AccountRegistrationV2Service service = mock(AccountRegistrationV2Service.class);
        SignupEmailOtpController controller = new SignupEmailOtpController(service);
        UUID challengeId = CHALLENGE_ID;
        given(service.issueEmailOtp("member@example.com", "long-enough-password", "member"))
                .willReturn(new SignupEmailOtpResult(challengeId, 300));

        // When
        var response = controller.issue(new SignupEmailOtpRequest(
                "member@example.com", "long-enough-password", "member"
        ));

        // Then
        then(response.getStatusCode().value()).isEqualTo(202);
        then(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        then(response.getBody()).isEqualTo(new SignupEmailOtpResponse(challengeId, 300));
    }

    @Test
    @DisplayName("회원가입 OTP 발급 문서")
    void signupOtp() throws Exception {
        given(signup.issueEmailOtp(EMAIL, PASSWORD, "member"))
                .willReturn(new SignupEmailOtpResult(ID, 300));
        success("signup/email-otp", basic(post("/api/v2/auth/signup/email-otp")).content(SIGNUP),
                202, true, otpFields(), signupFields(false));
    }

    @ParameterizedTest
    @EnumSource(value = EmailVerificationErrorCode.class, names = {"COOLDOWN_ACTIVE", "ISSUE_SUPERSEDED", "DELIVERY_UNAVAILABLE"})
    @DisplayName("OTP 발급 오류 응답 문서")
    void otpErrors(EmailVerificationErrorCode error) throws Exception {
        BusinessException exception = error == EmailVerificationErrorCode.COOLDOWN_ACTIVE
                ? new EmailVerificationCooldownException(60) : new BusinessException(error);
        MockHttpServletRequestBuilder request;
        int expectedStatus;
        given(signup.issueEmailOtp(EMAIL, PASSWORD, "member")).willThrow(exception);
        request = basic(post("/api/v2/auth/signup/email-otp")).content(SIGNUP);
        expectedStatus = switch (error) {
            case COOLDOWN_ACTIVE -> 429;
            case ISSUE_SUPERSEDED -> 409;
            default -> 503;
        };
        List<Snippet> snippets = new ArrayList<>();
        snippets.add(errorFields());
        if (error == EmailVerificationErrorCode.COOLDOWN_ACTIVE) {
            snippets.add(responseHeaders(headerWithName("Retry-After").description("재요청까지 대기할 초")));
        }
        var result = mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value(error.code()));
        if (error == EmailVerificationErrorCode.COOLDOWN_ACTIVE) {
            result.andExpect(header().string("Retry-After", "60"));
        }
        result.andDo(document("email-otp/v2/errors/" + error.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                preprocessRequest(modifyHeaders().set("Authorization", "REDACTED"), prettyPrint()),
                preprocessResponse(prettyPrint()), snippets.toArray(Snippet[]::new)));
    }

    private void success(String id, MockHttpServletRequestBuilder request, int statusCode,
                         boolean noStore, Snippet response, FieldDescriptor... fields) throws Exception {
        List<Snippet> snippets = new ArrayList<>();
        snippets.add(requestHeaders(headerWithName("Authorization").description(
                "Frontend Service Credential HTTP Basic")));
        if (fields.length > 0) snippets.add(requestFields(fields));
        if (response != null) snippets.add(response);
        if (noStore) snippets.add(responseHeaders(headerWithName("Cache-Control").description("no-store")));
        var result = mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(statusCode));
        if (noStore) result.andExpect(header().string("Cache-Control", "no-store"));
        result.andDo(document("email-otp/v2/" + id,
                preprocessRequest(modifyHeaders().set("Authorization",
                        "Basic ZnJvbnRlbmQ6PHBhc3N3b3JkPg=="), prettyPrint()),
                preprocessResponse(prettyPrint()), snippets.toArray(Snippet[]::new)));
    }

    private MockHttpServletRequestBuilder basic(MockHttpServletRequestBuilder request) {
        return request.with(httpBasic("frontend", "test-only-frontend-credential-password"));
    }

    private FieldDescriptor[] signupFields(boolean verified) {
        List<FieldDescriptor> fields = new ArrayList<>(List.of(
                fieldWithPath("email").description("이메일"),
                fieldWithPath("password").description("15~64자 비밀번호. 공백-only·제어 문자·UTF-8 72바이트 초과 입력 금지"),
                fieldWithPath("name").description("사용자 이름")));
        if (verified) fields.addAll(List.of(challenge(), code()));
        return fields.toArray(FieldDescriptor[]::new);
    }

    private FieldDescriptor challenge() {
        return fieldWithPath("challengeId").description("OTP 발급 응답의 Challenge UUID");
    }

    private FieldDescriptor code() {
        return fieldWithPath("code").description("이메일로 받은 숫자 6자리 인증번호");
    }

    private Snippet otpFields() {
        return responseFields(fieldWithPath("challengeId").description("Challenge UUID"),
                fieldWithPath("expiresInSeconds").description("인증번호 만료까지 남은 초"));
    }

    private Snippet errorFields() {
        return responseFields(fieldWithPath("code").description("오류 코드"),
                fieldWithPath("message").description("오류 설명"),
                fieldWithPath("path").description("요청 경로"),
                fieldWithPath("requestId").description("요청 추적 ID"));
    }
}
