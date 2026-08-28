package site.omagotchi.identityservice.account.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import site.omagotchi.identityservice.account.application.AccountRegistrationService;
import site.omagotchi.identityservice.account.application.AccountRegistrationV2Service;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.global.config.PasswordEncoderConfig;
import site.omagotchi.identityservice.global.security.basic.ServiceCredentialAuthenticationProviderFactory;
import site.omagotchi.identityservice.global.security.error.SecurityErrorResponseHandler;
import site.omagotchi.identityservice.global.security.frontend.FrontendCredentialProperties;
import site.omagotchi.identityservice.global.security.frontend.FrontendSecurityConfig;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AccountRegistrationController.class, AccountRegistrationV2Controller.class})
@Import({
        FrontendSecurityConfig.class,
        ServiceCredentialAuthenticationProviderFactory.class,
        PasswordEncoderConfig.class,
        SecurityErrorResponseHandler.class
})
@EnableConfigurationProperties(FrontendCredentialProperties.class)
@ActiveProfiles("test")
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
@DisplayName("v1 회원가입 호환 계약")
class AccountRegistrationControllerTest {

    private static final String SIGNUP_BODY = """
            {
              "email": "user@example.com",
              "password": "password-passphrase",
              "name": "사용자"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FrontendCredentialProperties frontendProperties;

    @MockitoBean
    private AccountRegistrationService accountRegistrationService;

    @MockitoBean
    private AccountRegistrationV2Service accountRegistrationV2Service;

    @Test
    @DisplayName("v1은 기존 세 필드만으로 가입하고 OTP 흐름을 호출하지 않음")
    void signsUpWithoutEmailOtp() throws Exception {
        Account account = mock(Account.class);
        given(account.getId()).willReturn(UUID.fromString("00000000-0000-0000-0000-000000000635"));
        given(account.getEmail()).willReturn("user@example.com");
        given(account.getName()).willReturn("사용자");
        given(account.getGlobalRole()).willReturn(GlobalRole.USER);
        given(account.getStatus()).willReturn(AccountStatus.ACTIVE);
        given(account.getCreatedAt()).willReturn(Instant.parse("2026-08-28T00:00:00Z"));
        given(account.getUpdatedAt()).willReturn(Instant.parse("2026-08-28T00:00:00Z"));
        given(accountRegistrationService.signUp(
                "user@example.com", "password-passphrase", "사용자"
        )).willReturn(account);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(httpBasic(frontendProperties.username(), frontendProperties.password()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpectAll(
                        status().isCreated(),
                        jsonPath("$.email").value("user@example.com"),
                        jsonPath("$.role").value("USER")
                )
                .andDo(document(
                        "auth/signup/success",
                        preprocessRequest(
                                modifyHeaders().set(HttpHeaders.AUTHORIZATION, "Basic cmVkYWN0ZWQ6cmVkYWN0ZWQ="),
                                prettyPrint(),
                                replacePattern(
                                        Pattern.compile("\"password\"\\s*:\\s*\"[^\"]*\""),
                                        "\"password\" : \"<password>\""
                                )
                        ),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION)
                                .description("Frontend Service HTTP Basic Credential")),
                        requestFields(
                                fieldWithPath("email").description("가입할 이메일"),
                                fieldWithPath("password").description("가입 비밀번호"),
                                fieldWithPath("name").description("표시 이름")
                        )
                ));

        verify(accountRegistrationService).signUp("user@example.com", "password-passphrase", "사용자");
        verifyNoInteractions(accountRegistrationV2Service);
    }

    @Test
    @DisplayName("v1 요청을 v2에 보내면 OTP 필수값 누락으로 거부")
    void rejectsV1PayloadOnV2Signup() throws Exception {
        mockMvc.perform(post("/api/v2/auth/signup")
                        .with(httpBasic(frontendProperties.username(), frontendProperties.password()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("COMMON_INVALID_REQUEST")
                );

        verifyNoInteractions(accountRegistrationService, accountRegistrationV2Service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/signup/email-otp",
            "/api/v1/auth/signup/email-verification/challenges"
    })
    @DisplayName("v1에는 이메일 OTP 요청 경로를 제공하지 않음")
    void doesNotExposeEmailOtpOnV1(String path) throws Exception {
        mockMvc.perform(post(path)
                        .with(httpBasic(frontendProperties.username(), frontendProperties.password()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isNotFound());

        verifyNoInteractions(accountRegistrationService, accountRegistrationV2Service);
    }
}
