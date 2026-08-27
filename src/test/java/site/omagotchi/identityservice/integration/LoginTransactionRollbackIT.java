package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.port.AccessTokenIssuer;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({
        TestcontainersConfig.class,
        TestJwtConfig.class,
        LoginTransactionRollbackIT.FailingAccessTokenConfig.class
})
class LoginTransactionRollbackIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    private AuthApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Token 발급 실패 시 성공 기록과 Refresh Token 저장 Rollback")
    void rollsBackLoginWhenTokenIssuanceFails() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        api.login("user@example.com", "wrong-password1")
                .andExpect(status().isUnauthorized());

        // When
        api.login("user@example.com", "password-passphrase").andExpectAll(
                status().isInternalServerError(),
                jsonPath("$.code").value("COMMON_INTERNAL_SERVER_ERROR")
        );
        Account account = accountJpaRepository.findById(accountId).orElseThrow();

        // Then
        thenSoftly(softly -> {
            softly.then(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(account.getFailedLoginAttempts()).isEqualTo((short) 1);
            softly.then(refreshTokenJpaRepository.findAll()).isEmpty();
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAccessTokenConfig {

        @Bean
        @Primary
        AccessTokenIssuer failingAccessTokenIssuer() {
            return (accountId, globalRole) -> {
                throw new IllegalStateException("의도한 Access Token 발급 실패");
            };
        }
    }
}
