package site.omagotchi.identityservice;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class AuthApiTestClient {

    static final String FRONTEND_USERNAME = "frontend";
    static final String FRONTEND_PASSWORD = "test-only-frontend-credential-password";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    AuthApiTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    ResultActions signUp(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                .with(httpBasic(FRONTEND_USERNAME, FRONTEND_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email)));
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

    private String signupBody(String email) {
        return """
                {
                  "email": "%s",
                  "password": "password-passphrase",
                  "name": "홍길동"
                }
                """.formatted(email);
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

    record TokenBundle(
            UUID userId,
            String globalRole,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
    }
}
