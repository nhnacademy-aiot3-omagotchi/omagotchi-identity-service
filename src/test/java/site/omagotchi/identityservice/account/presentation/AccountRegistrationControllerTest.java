package site.omagotchi.identityservice.account.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.identityservice.account.application.AccountRegistrationService;
import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("회원가입 이메일 인증 API")
class AccountRegistrationControllerTest {

    private MockMvc mockMvc;
    private AccountRegistrationService accountRegistrationService;

    @BeforeEach
    void setUp() {
        accountRegistrationService = mock(AccountRegistrationService.class);
        AccountRegistrationController controller = new AccountRegistrationController(
                accountRegistrationService
        );
        mockMvc = standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("가입 입력 사전 검사 후 SIGN_UP OTP Challenge 요청")
    void requestsSignUpEmailChallenge() throws Exception {
        given(accountRegistrationService.requestEmailVerification(
                "user@example.com",
                "password-passphrase",
                "사용자"
        )).willReturn(new EmailVerificationChallengeResult("challenge-id", 600L));

        mockMvc.perform(post("/api/v1/auth/signup/email-verification/challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password-passphrase",
                                  "name": "사용자"
                                }
                                """))
                .andExpectAll(
                        status().isAccepted(),
                        header().string(HttpHeaders.CACHE_CONTROL, "no-store"),
                        jsonPath("$.challengeId").value("challenge-id"),
                        jsonPath("$.expiresInSeconds").value(600L)
                );

        verify(accountRegistrationService).requestEmailVerification(
                "user@example.com",
                "password-passphrase",
                "사용자"
        );
    }
}
