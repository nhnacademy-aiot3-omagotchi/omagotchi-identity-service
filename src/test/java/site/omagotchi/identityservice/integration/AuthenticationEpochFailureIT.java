package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.port.AuthenticationEpochStore;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class AuthenticationEpochFailureIT {

    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private AuthenticationEpochStore authenticationEpochStore;

    @Autowired
    @Qualifier("redisContainer")
    private GenericContainer<?> redisContainer;

    private AuthApiTestClient api;
    private boolean redisPaused;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @AfterEach
    void resumeRedis() {
        if (!redisPaused) {
            return;
        }
        try (var command = redisContainer.getDockerClient()
                .unpauseContainerCmd(redisContainer.getContainerId())) {
            command.exec();
        }
        redisPaused = false;
    }

    @Test
    @DisplayName("로그인 Epoch 조회 장애 시 503과 Token 미발급")
    void failsClosedDuringLogin() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        pauseRedis();

        // When
        ResultActions response = api.login("user@example.com", CURRENT_PASSWORD);
        resumeRedis();

        // Then
        response.andExpectAll(
                status().isServiceUnavailable(),
                header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE),
                jsonPath("$.code").value("COMMON_SERVICE_UNAVAILABLE")
        );
        thenSoftly(softly -> {
            softly.then(refreshTokenJpaRepository.findAll()).isEmpty();
            softly.then(authenticationEpochStore.find(accountId)).isEmpty();
        });
    }

    @Test
    @DisplayName("Refresh Epoch 조회 장애 시 503과 Token 미소비")
    void failsClosedBeforeRefreshTokenConsumption() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                CURRENT_PASSWORD
        );
        pauseRedis();

        // When
        ResultActions response = api.refresh(login.refreshToken());
        resumeRedis();
        RefreshToken originalToken = refreshTokenJpaRepository.findAll().getFirst();

        // Then
        response.andExpectAll(
                status().isServiceUnavailable(),
                jsonPath("$.code").value("COMMON_SERVICE_UNAVAILABLE")
        );
        thenSoftly(softly -> {
            softly.then(refreshTokenJpaRepository.findAll()).hasSize(1);
            softly.then(originalToken.isUsed()).isFalse();
            softly.then(originalToken.isRevoked()).isFalse();
        });
    }

    @Test
    @DisplayName("비밀번호 변경 Epoch 응답 장애 시 DB 변경 Rollback")
    void rollsBackPasswordAndRevocationWhenEpochRotationFails() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                CURRENT_PASSWORD
        );
        String previousPasswordHash = accountJpaRepository.findById(accountId)
                .orElseThrow()
                .getPasswordHash();
        pauseRedis();

        // When
        ResultActions response = api.changePassword(
                login.accessToken(),
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );
        resumeRedis();
        Account account = accountJpaRepository.findById(accountId).orElseThrow();
        RefreshToken refreshToken = refreshTokenJpaRepository.findAll().getFirst();

        // Then
        response.andExpectAll(
                status().isServiceUnavailable(),
                jsonPath("$.code").value("COMMON_SERVICE_UNAVAILABLE")
        );
        thenSoftly(softly -> {
            softly.then(account.getPasswordHash()).isEqualTo(previousPasswordHash);
            softly.then(passwordHasher.matches(CURRENT_PASSWORD, account.getPasswordHash()))
                    .isTrue();
            softly.then(refreshToken.isRevoked()).isFalse();
        });
    }

    @Test
    @DisplayName("재사용 탐지는 Epoch 조회 장애보다 먼저 Family를 폐기")
    void preservesReuseDetectionWhenEpochStoreIsUnavailable() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                CURRENT_PASSWORD
        );
        api.refreshSuccessfully(login.refreshToken());
        UUID familyId = refreshTokenJpaRepository.findAll().getFirst().getFamilyId();
        pauseRedis();

        // When
        ResultActions response = api.refresh(login.refreshToken());
        resumeRedis();
        List<RefreshToken> family = refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getFamilyId().equals(familyId))
                .toList();

        // Then
        response.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        thenSoftly(softly -> {
            softly.then(family).hasSize(2);
            softly.then(family).allSatisfy(token -> {
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevocationReason())
                        .isEqualTo(RefreshTokenRevocationReason.REUSE_DETECTED);
            });
        });
    }

    private void pauseRedis() {
        try (var command = redisContainer.getDockerClient()
                .pauseContainerCmd(redisContainer.getContainerId())) {
            command.exec();
        }
        redisPaused = true;
    }
}
