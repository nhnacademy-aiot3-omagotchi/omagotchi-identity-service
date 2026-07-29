package site.omagotchi.identityservice;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.auth.presentation.RefreshTokenCookieFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class AuthApiTestClient {

    static final String ALLOWED_ORIGIN = "http://localhost:8080";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    AuthApiTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    ResultActions signUp(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email)));
    }

    ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)));
    }

    ResultActions refresh(Cookie refreshCookie) throws Exception {
        return refresh(refreshCookie, ALLOWED_ORIGIN);
    }

    ResultActions refresh(Cookie refreshCookie, String origin) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .header(HttpHeaders.ORIGIN, origin)
                .cookie(refreshCookie));
    }

    ResultActions logout(Cookie refreshCookie) throws Exception {
        return logout(refreshCookie, ALLOWED_ORIGIN);
    }

    ResultActions logout(Cookie refreshCookie, String origin) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.ORIGIN, origin)
                .cookie(refreshCookie));
    }

    UUID signupSuccessfully(String email) throws Exception {
        String response = signUp(email)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("userId").asString());
    }

    LoginTokens loginSuccessfully(String email, String password) throws Exception {
        var response = login(email, password)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        return readTokens(response.getContentAsString(), response.getCookie(
                RefreshTokenCookieFactory.COOKIE_NAME
        ));
    }

    LoginTokens refreshSuccessfully(Cookie refreshCookie) throws Exception {
        var response = refresh(refreshCookie)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        return readTokens(response.getContentAsString(), response.getCookie(
                RefreshTokenCookieFactory.COOKIE_NAME
        ));
    }

    private LoginTokens readTokens(String content, Cookie refreshCookie) throws Exception {
        JsonNode json = objectMapper.readTree(content);
        then(refreshCookie).isNotNull();

        return new LoginTokens(
                json.get("accessToken").asString(),
                refreshCookie.getValue(),
                refreshCookie
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

    record LoginTokens(
            String accessToken,
            String refreshToken,
            Cookie refreshCookie
    ) {
    }
}
