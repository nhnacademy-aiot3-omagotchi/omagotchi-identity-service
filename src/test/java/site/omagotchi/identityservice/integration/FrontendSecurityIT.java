package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class FrontendSecurityIT {

    private static final String WRONG_FRONTEND_PASSWORD =
            "wrong-test-only-frontend-credential-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    private AuthApiTestClient authApi;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiTestClient(mockMvc, objectMapper);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Token 수명주기 API는 Frontend Credential 요구")
    void requiresFrontendCredential() throws Exception {
        // Given
        String requestBody = loginBody("user@example.com", "password-passphrase");

        // When
        ResultActions missingCredential = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
        ResultActions wrongCredential = mockMvc.perform(post("/api/v1/auth/login")
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        WRONG_FRONTEND_PASSWORD
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // Then
        missingCredential.andExpectAll(
                status().isUnauthorized(),
                header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Basic")),
                header().doesNotExist(HttpHeaders.SET_COOKIE),
                jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"),
                jsonPath("$.path").value("/api/v1/auth/login")
        );
        wrongCredential.andExpectAll(
                status().isUnauthorized(),
                header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Basic")),
                header().doesNotExist(HttpHeaders.SET_COOKIE),
                jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
        );
    }

    @Test
    @DisplayName("Frontend 로그인에 Token Bundle 반환")
    void issuesTokenBundleWithoutBrowserCookie() throws Exception {
        // Given
        UUID userId = authApi.signupSuccessfully("user@example.com");

        // When
        AuthApiTestClient.TokenBundle response = authApi.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        Jwt accessToken = jwtDecoder.decode(response.accessToken());

        // Then
        thenSoftly(softly -> {
            softly.then(response.userId()).isEqualTo(userId);
            softly.then(response.globalRole()).isEqualTo("USER");
            softly.then(response.refreshToken()).isNotBlank();
            softly.then(response.accessTokenExpiresAt())
                    .isEqualTo(accessToken.getExpiresAt());
            softly.then(response.refreshTokenExpiresAt())
                    .isAfter(response.accessTokenExpiresAt());
            softly.then(accessToken.getSubject()).isEqualTo(userId.toString());
            softly.then(accessToken.getClaimAsString("role")).isEqualTo("USER");
        });
    }

    @Test
    @DisplayName("Refresh Token 없는 갱신 요청은 401")
    void rejectsRefreshWithoutTokenAsAuthenticationFailure() throws Exception {
        // When
        ResultActions response = requestWithoutRefreshToken("refresh");

        // Then
        response.andExpectAll(
                status().isUnauthorized(),
                header().doesNotExist(HttpHeaders.SET_COOKIE),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"),
                jsonPath("$.path").value("/api/v1/auth/refresh")
        );
    }

    @Test
    @DisplayName("Refresh Token 없는 로그아웃 요청은 멱등하게 204")
    void succeedsLogoutWithoutRefreshToken() throws Exception {
        // When
        ResultActions response = requestWithoutRefreshToken("logout");

        // Then
        response.andExpectAll(
                status().isNoContent(),
                header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")),
                header().doesNotExist(HttpHeaders.SET_COOKIE)
        );
    }

    @Test
    @DisplayName("Frontend Basic 인증과 사용자 Bearer 인증 경계 분리")
    void separatesFrontendAndResourceServerAuthentication() throws Exception {
        // When
        ResultActions bearerOnFrontendAuth = mockMvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-used-by-frontend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("user@example.com", "password-passphrase")));
        ResultActions basicOnResourceApi = mockMvc.perform(get("/api/v1/users/me")
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        AuthApiTestClient.FRONTEND_PASSWORD
                )));

        // Then
        bearerOnFrontendAuth.andExpectAll(
                status().isUnauthorized(),
                header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Basic"))
        );
        basicOnResourceApi.andExpectAll(
                status().isUnauthorized(),
                header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer"))
        );
    }

    private ResultActions requestWithoutRefreshToken(String operation) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/" + operation)
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        AuthApiTestClient.FRONTEND_PASSWORD
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": ""}
                        """));
    }

    private String loginBody(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
