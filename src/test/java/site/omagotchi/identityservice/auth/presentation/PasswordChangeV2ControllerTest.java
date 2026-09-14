package site.omagotchi.identityservice.auth.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import site.omagotchi.identityservice.auth.application.PasswordChangeV2Service;
import site.omagotchi.identityservice.auth.application.result.PasswordChangeEmailOtpResult;
import site.omagotchi.identityservice.auth.infrastructure.JwtAccessTokenIssuer;
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

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;
import static org.springframework.restdocs.headers.HeaderDocumentation.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PasswordChangeV2Controller.class)
@Import({PasswordEncoderConfig.class, FrontendSecurityConfig.class, ServiceCredentialAuthenticationProviderFactory.class,
        JwtSecurityConfig.class, JwtConfig.class, JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class, TestJwtConfig.class})
@EnableConfigurationProperties({JwtProperties.class, FrontendCredentialProperties.class})
@ActiveProfiles("test")
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
class PasswordChangeV2ControllerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000700201");
    private static final String PASSWORD = "long-enough-password";
    private static final String CHANGE = """
            {"currentPassword":"long-enough-password","newPassword":"new-password-passphrase",
             "challengeId":"00000000-0000-0000-0000-000000700201","code":"123456"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JwtProperties jwtProperties;
    @MockitoBean
    private PasswordChangeV2Service change;
    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Test
    @DisplayName("비밀번호 변경 OTP 발급 문서")
    void passwordChangeOtp() throws Exception {
        given(change.issueEmailOtp(ID)).willReturn(new PasswordChangeEmailOtpResult(ID, 300));
        success("password-change/email-otp",
                bearer(post("/api/v2/users/me/password/email-otp")), 202, true, otpFields());
    }

    @Test
    @DisplayName("OTP 인증 비밀번호 변경 문서")
    void passwordChange() throws Exception {
        success("password-change/success",
                bearer(patch("/api/v2/users/me/password")).content(CHANGE),
                204, true, null, fieldWithPath("currentPassword").description("현재 비밀번호"),
                newPassword(), challenge(), code());
        then(change).should().changePassword(ID, PASSWORD, "new-password-passphrase", ID, "123456");
    }

    @Test
    @DisplayName("유효하지 않은 OTP 오류 문서")
    void invalidChallenge() throws Exception {
        willThrow(new BusinessException(EmailVerificationErrorCode.INVALID_CHALLENGE))
                .given(change).changePassword(any(), anyString(), anyString(), any(), anyString());
        mockMvc.perform(bearer(patch("/api/v2/users/me/password")).contentType(MediaType.APPLICATION_JSON).content(CHANGE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID_CHALLENGE"))
                .andDo(document("email-otp/v2/errors/invalid-challenge",
                        preprocessRequest(modifyHeaders().set("Authorization", "Bearer <access-token>"), prettyPrint()),
                        preprocessResponse(prettyPrint()), errorFields()));
    }

    private void success(String id, MockHttpServletRequestBuilder request, int statusCode,
                         boolean noStore, Snippet response, FieldDescriptor... fields) throws Exception {
        List<Snippet> snippets = new ArrayList<>();
        snippets.add(requestHeaders(headerWithName("Authorization").description(
                "사용자의 Access JWT Bearer Token")));
        if (fields.length > 0) snippets.add(requestFields(fields));
        if (response != null) snippets.add(response);
        if (noStore) snippets.add(responseHeaders(headerWithName("Cache-Control").description("no-store")));
        var result = mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(statusCode));
        if (noStore) result.andExpect(header().string("Cache-Control", "no-store"));
        result.andDo(document("email-otp/v2/" + id,
                preprocessRequest(modifyHeaders().set("Authorization",
                        "Bearer <access-token>"), prettyPrint()),
                preprocessResponse(prettyPrint()), snippets.toArray(Snippet[]::new)));
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request) {
        String token = new JwtAccessTokenIssuer(jwtEncoder, jwtProperties, Clock.systemUTC())
                .issue(ID, "USER").value();
        return request.header("Authorization", "Bearer " + token);
    }

    private FieldDescriptor newPassword() {
        return fieldWithPath("newPassword").description("현재와 다른 15~64자 새 비밀번호. 공백-only·제어 문자·UTF-8 72바이트 초과 입력 금지");
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
