package site.omagotchi.identityservice.integration;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import site.omagotchi.identityservice.global.exception.BusinessException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AuthApiTestClient api;
    private AccountStateTestFixture accountStateFixture;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        accountStateFixture = new AccountStateTestFixture(jdbcTemplate);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입·로그인·본인 조회")
    void signupLoginAndGetMe() throws Exception {
        // Given
        String signupEmail = "  USER@Example.COM  ";
        String loginEmail = "  USER@EXAMPLE.COM  ";

        // When
        UUID userId = api.signupSuccessfully(signupEmail);
        Account account = accountJpaRepository.findById(userId).orElseThrow();
        String accessToken = api.loginSuccessfully(
                loginEmail,
                "password-passphrase"
        ).accessToken();
        Jwt jwt = jwtDecoder.decode(accessToken);
        ResultActions meResponse = mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));

        // Then
        thenSoftly(softly -> {
            softly.then(account.getEmail()).isEqualTo("user@example.com");
            softly.then(passwordHasher.matches(
                    "password-passphrase",
                    account.getPasswordHash()
            )).isTrue();
            softly.then(userId.version()).isEqualTo(4);
            softly.then(jwt.getSubject()).isEqualTo(userId.toString());
            softly.then(jwt.getClaimAsString("role")).isEqualTo("USER");
        });
        meResponse.andExpectAll(
                status().isOk(),
                jsonPath("$.userId").value(userId.toString())
        );
    }

    @Test
    @DisplayName("중복 가입·잘못된 로그인 오류")
    void rejectsDuplicateSignupAndInvalidLogin() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");

        // When
        ResultActions duplicateSignup = api.signUp("  USER@EXAMPLE.COM  ");
        ResultActions missingAccountLogin = api.login(
                "missing@example.com",
                "wrong-password1"
        );
        ResultActions wrongPasswordLogin = api.login(
                "user@example.com",
                "wrong-password1"
        );

        // Then
        duplicateSignup.andExpectAll(
                status().isConflict(),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                header().string("X-Content-Type-Options", "nosniff"),
                jsonPath("$.code").value("ACCOUNT_DUPLICATE_EMAIL")
        );
        missingAccountLogin.andExpectAll(
                status().isUnauthorized(),
                header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE),
                jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS")
        );
        wrongPasswordLogin.andExpectAll(
                status().isUnauthorized(),
                header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE),
                jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS")
        );
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"LOCKED", "DISABLED", "WITHDRAWN"})
    @DisplayName("로그인 불가 계정 상태 거부")
    void rejectsLoginForUnavailableAccount(AccountStatus accountStatus) throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        accountStateFixture.changeStatus(accountId, accountStatus);

        // When
        ResultActions response = api.login(
                "user@example.com",
                "password-passphrase"
        );

        // Then
        response.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS")
        );
    }

    @Test
    @DisplayName("DB 이메일 UNIQUE 위반을 업무 실패로 직접 변환")
    void translatesDatabaseEmailConflict() {
        // Given
        accountJpaRepository.saveAndFlush(Account.register(
                "user@example.com",
                "encoded-password",
                "첫 계정"
        ));
        Account duplicate = Account.register(
                "user@example.com",
                "encoded-password",
                "두 번째 계정"
        );

        // When
        Throwable thrown = catchThrowable(() -> accountRepository.create(duplicate));

        // Then
        then(thrown)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        then(exception.getErrorCode()).isEqualTo(AccountErrorCode.DUPLICATE_EMAIL)
                )
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("인증 정보 누락·오류 요청 거부")
    void rejectsInvalidAuthentication() throws Exception {
        // Given
        String invalidAccessToken = "invalid-token";

        // When
        ResultActions missingTokenResponse = mockMvc.perform(get("/api/v1/users/me"));
        ResultActions invalidTokenResponse = mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidAccessToken));

        // Then
        missingTokenResponse.andExpectAll(
                status().isUnauthorized(),
                header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")),
                jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
        );
        invalidTokenResponse.andExpectAll(
                status().isUnauthorized(),
                header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")),
                header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        containsString("error=\"invalid_token\"")
                ),
                jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
        );
    }

    @Test
    @DisplayName("ERROR 디스패치 원본 오류 유지")
    void allowsErrorDispatchWithoutAuthentication() throws Exception {
        // Given
        int originalStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();

        // When
        ResultActions response = mockMvc.perform(get("/error")
                .accept(MediaType.APPLICATION_JSON)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, originalStatus)
                .with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    return request;
                }));

        // Then
        response.andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("일반 /error 요청 인증 요구")
    void rejectsDirectErrorRequestWithoutAuthentication() throws Exception {
        // Given
        String errorPath = "/error";

        // When
        ResultActions response = mockMvc.perform(get(errorPath)
                .accept(MediaType.APPLICATION_JSON));

        // Then
        response.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
        );
    }

    @Test
    @DisplayName("회원가입 이메일 정책 위반의 공개 오류")
    void rejectsInvalidSignupEmail() throws Exception {
        // Given
        String invalidEmail = "not-an-email";

        // When
        ResultActions response = api.signUp(invalidEmail);

        // Then
        response.andExpectAll(
                status().isBadRequest(),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                header().string("X-Content-Type-Options", "nosniff"),
                jsonPath("$.path").value("/api/v1/auth/signup"),
                jsonPath("$.code").value("ACCOUNT_INVALID_EMAIL"),
                jsonPath("$.message").value(
                        "이메일은 올바른 주소 형식의 254자 이하여야 합니다."
                )
        );
    }

    @Test
    @DisplayName("회원가입 비밀번호 정책 위반의 공개 오류")
    void rejectsInvalidSignupPassword() throws Exception {
        // When
        ResultActions response = api.signUp(
                "user@example.com",
                " ".repeat(15),
                "홍길동"
        );

        // Then
        response.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("ACCOUNT_INVALID_PASSWORD"),
                jsonPath("$.message").value(
                        "비밀번호는 15~64자이며 공백만으로 구성하거나 제어 문자를 포함할 수 없습니다. "
                                + "한글 등 일부 문자는 더 짧게 입력해야 합니다."
                )
        );
    }

    @Test
    @DisplayName("회원가입 이름 정책 위반의 공개 오류")
    void rejectsInvalidSignupName() throws Exception {
        // When
        ResultActions response = api.signUp(
                "user@example.com",
                "password-passphrase",
                "가".repeat(31)
        );

        // Then
        response.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("ACCOUNT_INVALID_NAME"),
                jsonPath("$.message").value(
                        "이름은 앞뒤 공백을 제외하고 1~30자여야 합니다."
                )
        );
    }

    @Test
    @DisplayName("Spring MVC 기본 오류 상태와 Header 유지")
    void preservesSpringMvcErrorStatusAndHeaders() throws Exception {
        // When
        ResultActions methodNotAllowed = mockMvc.perform(get("/api/v1/auth/signup")
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        AuthApiTestClient.FRONTEND_PASSWORD
                ))
                .accept(MediaType.APPLICATION_JSON));
        ResultActions unsupportedMediaType = mockMvc.perform(post("/api/v1/auth/signup")
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        AuthApiTestClient.FRONTEND_PASSWORD
                ))
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content("{}"));

        // Then
        methodNotAllowed.andExpectAll(
                status().isMethodNotAllowed(),
                header().string(HttpHeaders.ALLOW, "POST"),
                jsonPath("$.code").value("COMMON_INVALID_REQUEST")
        );
        unsupportedMediaType.andExpectAll(
                status().isUnsupportedMediaType(),
                jsonPath("$.code").value("COMMON_INVALID_REQUEST")
        );
    }

    @Test
    @DisplayName("읽을 수 없는 JSON 오류 계약 유지")
    void preservesMalformedRequestContract() throws Exception {
        // When
        ResultActions response = mockMvc.perform(post("/api/v1/auth/signup")
                .with(httpBasic(
                        AuthApiTestClient.FRONTEND_USERNAME,
                        AuthApiTestClient.FRONTEND_PASSWORD
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{"));

        // Then
        response.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("COMMON_MALFORMED_REQUEST")
        );
    }

}
