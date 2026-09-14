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
import site.omagotchi.identityservice.account.application.result.AccountRegistrationResult;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.presentation.request.SignupV2Request;
import site.omagotchi.identityservice.global.config.PasswordEncoderConfig;
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

import java.time.Instant;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountRegistrationV2Controller.class)
@Import({PasswordEncoderConfig.class, FrontendSecurityConfig.class, ServiceCredentialAuthenticationProviderFactory.class,
        JwtSecurityConfig.class, JwtConfig.class, JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class, TestJwtConfig.class})
@EnableConfigurationProperties({JwtProperties.class, FrontendCredentialProperties.class})
@ActiveProfiles("test")
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
class AccountRegistrationV2ControllerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000700201");
    private static final String EMAIL = "member@example.com";
    private static final String PASSWORD = "long-enough-password";
    private static final String VERIFIED_SIGNUP = """
            {"email":"member@example.com","password":"long-enough-password","name":"member",
             "challengeId":"00000000-0000-0000-0000-000000700201","code":"123456"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AccountRegistrationV2Service signup;
    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700201"
    );

    @Test
    @DisplayName("이메일 인증 회원가입 성공은 201 응답")
    void returnsCreatedAccount() {
        // Given
        AccountRegistrationV2Service service = mock(AccountRegistrationV2Service.class);
        AccountRegistrationV2Controller controller = new AccountRegistrationV2Controller(service);
        UUID challengeId = CHALLENGE_ID;
        Account account = Account.register(
                "member@example.com", "password-hash", "member", Instant.EPOCH
        );
        given(service.signUp(
                "member@example.com", "long-enough-password", "member", challengeId, "123456"
        )).willReturn(new AccountRegistrationResult(
                account,
                AccountRegistrationResult.Outcome.CREATED
        ));

        // When
        var response = controller.signUp(new SignupV2Request(
                "member@example.com",
                "long-enough-password",
                "member",
                challengeId,
                "123456"
        ));

        // Then
        then(response.getStatusCode().value()).isEqualTo(201);
        then(response.getBody().email()).isEqualTo("member@example.com");
    }

    @ParameterizedTest
    @EnumSource(AccountRegistrationResult.Outcome.class)
    @DisplayName("신규 가입 및 탈퇴 계정 복구 문서")
    void signupCompletion(AccountRegistrationResult.Outcome outcome) throws Exception {
        given(signup.signUp(EMAIL, PASSWORD, "member", ID, "123456"))
                .willReturn(new AccountRegistrationResult(
                        Account.register(EMAIL, "password-hash", "member", Instant.EPOCH), outcome));
        boolean created = outcome == AccountRegistrationResult.Outcome.CREATED;
        success("signup/" + (created ? "created" : "recovered"),
                basic(post("/api/v2/auth/signup")).content(VERIFIED_SIGNUP),
                created ? 201 : 200, false,
                responseFields(fieldWithPath("userId").description("계정 UUID"),
                        fieldWithPath("email").description("이메일"),
                        fieldWithPath("name").description("이름"),
                        fieldWithPath("role").description("전역 역할"),
                        fieldWithPath("status").description("계정 상태"),
                        fieldWithPath("createdAt").description("생성 시각"),
                        fieldWithPath("updatedAt").description("수정 시각")), signupFields(true));
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

}
