package site.omagotchi.identityservice.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.account.domain.EmailPolicy;
import site.omagotchi.identityservice.email.application.EmailVerificationReservationResult;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RequiredArgsConstructor
final class AuthApiTestClient {

    static final String FRONTEND_USERNAME = "frontend";
    static final String FRONTEND_PASSWORD = "test-only-frontend-credential-password";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final EmailVerificationRepository emailVerificationRepository;

    ResultActions signUp(String email) throws Exception {
        return signUp(email, "password-passphrase", "홍길동");
    }

    ResultActions signUp(String email, String password, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                .with(httpBasic(FRONTEND_USERNAME, FRONTEND_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email, password, name)));
    }

    ResultActions signUpWithCode(
            String email,
            String password,
            String name,
            String challengeId,
            String code
    ) throws Exception {
        return mockMvc.perform(post("/api/v2/auth/signup")
                .with(httpBasic(FRONTEND_USERNAME, FRONTEND_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email, password, name, challengeId, code)));
    }

    ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(httpBasic(FRONTEND_USERNAME, FRONTEND_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)));
    }

    ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .with(httpBasic(FRONTEND_USERNAME, FRONTEND_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refreshToken)));
    }

    ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .with(httpBasic(FRONTEND_USERNAME, FRONTEND_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refreshToken)));
    }

    ResultActions changePassword(
            String accessToken,
            String currentPassword,
            String newPassword
    ) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(passwordChangeBody(currentPassword, newPassword)));
    }

    ResultActions changePasswordWithCode(
            String accessToken,
            String currentPassword,
            String newPassword,
            String challengeId,
            String code
    ) throws Exception {
        return mockMvc.perform(patch("/api/v2/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(passwordChangeBody(
                        currentPassword,
                        newPassword,
                        challengeId,
                        code
                )));
    }

    ResultActions changeName(String accessToken, String name) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(nameChangeBody(name)));
    }

    UUID signupSuccessfully(String email) throws Exception {
        String response = signUp(email)
                .andExpectAll(
                        status().isCreated(),
                        header().string("X-Content-Type-Options", "nosniff"),
                        header().string(HttpHeaders.CONTENT_TYPE, containsString("application/json"))
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("userId").asString());
    }

    TokenBundle loginSuccessfully(String email, String password) throws Exception {
        MockHttpServletResponse response = login(email, password)
                .andExpectAll(
                        status().isOk(),
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                        header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")),
                        header().doesNotExist(HttpHeaders.SET_COOKIE)
                )
                .andReturn()
                .getResponse();

        return readTokenBundle(response.getContentAsString());
    }

    TokenBundle refreshSuccessfully(String refreshToken) throws Exception {
        MockHttpServletResponse response = refresh(refreshToken)
                .andExpectAll(
                        status().isOk(),
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                        header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")),
                        header().doesNotExist(HttpHeaders.SET_COOKIE)
                )
                .andReturn()
                .getResponse();

        return readTokenBundle(response.getContentAsString());
    }

    private TokenBundle readTokenBundle(String content) throws Exception {
        JsonNode json = objectMapper.readTree(content);
        then(json.get("refreshToken").asString()).isNotBlank();

        return new TokenBundle(
                UUID.fromString(json.get("userId").asString()),
                json.get("globalRole").asString(),
                json.get("accessToken").asString(),
                Instant.parse(json.get("accessTokenExpiresAt").asString()),
                json.get("refreshToken").asString(),
                Instant.parse(json.get("refreshTokenExpiresAt").asString())
        );
    }

    OtpProof otp(String email, VerificationPurpose purpose) {
        OtpProof proof = new OtpProof(UUID.randomUUID().toString(), "123456");
        EmailVerificationReservationResult reservation =
                emailVerificationRepository.reserveChallenge(
                purpose,
                EmailPolicy.normalize(email),
                new OtpChallenge(proof.challengeId(), proof.code()),
                Duration.ofMinutes(10),
                Duration.ofMillis(1)
        );
        then(reservation.reserved()).isTrue();
        return proof;
    }

    private String signupBody(String email, String password, String name) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "name": "%s"
                }
                """.formatted(email, password, name);
    }

    private String signupBody(
            String email,
            String password,
            String name,
            String challengeId,
            String code
    ) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "name": "%s",
                  "challengeId": "%s",
                  "code": "%s"
                }
                """.formatted(email, password, name, challengeId, code);
    }

    private String loginBody(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private String refreshBody(String refreshToken) {
        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);
    }

    private String passwordChangeBody(String currentPassword, String newPassword) {
        return """
                {
                  "currentPassword": "%s",
                  "newPassword": "%s"
                }
                """.formatted(currentPassword, newPassword);
    }

    private String passwordChangeBody(
            String currentPassword,
            String newPassword,
            String challengeId,
            String code
    ) {
        return """
                {
                  "currentPassword": "%s",
                  "newPassword": "%s",
                  "challengeId": "%s",
                  "code": "%s"
                }
                """.formatted(currentPassword, newPassword, challengeId, code);
    }

    private String nameChangeBody(String name) {
        return """
                {
                  "name": "%s"
                }
                """.formatted(name);
    }

    record TokenBundle(
            UUID userId,
            String globalRole,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
    }

    record OtpProof(String challengeId, String code) {
    }
}
