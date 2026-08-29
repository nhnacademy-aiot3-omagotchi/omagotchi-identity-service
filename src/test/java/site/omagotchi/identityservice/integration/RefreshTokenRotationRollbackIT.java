package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.session.RefreshTokenHasher;
import site.omagotchi.identityservice.auth.application.session.RefreshTokenRotation;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class RefreshTokenRotationRollbackIT {

    private static final String PASSWORD = "password-passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private RefreshTokenRotation refreshTokenRotation;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private AuthApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Token 묶음 발급 후 외부 Transaction 실패 시 Refresh 회전 Rollback")
    void rollsBackRotationWhenOuterTransactionFailsAfterTokenIssuance() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                PASSWORD
        );

        // When
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Throwable thrown = catchThrowable(() -> transaction.executeWithoutResult(status -> {
            refreshTokenRotation.rotate(login.refreshToken()).orElseThrow();
            throw new IllegalStateException("의도한 외부 Transaction 실패");
        }));

        // Then
        String originalTokenHash = refreshTokenHasher.hash(login.refreshToken());
        RefreshToken originalToken = refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getTokenHash().equals(originalTokenHash))
                .findFirst()
                .orElseThrow();
        thenSoftly(softly -> {
            softly.then(thrown)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("의도한 외부 Transaction 실패");
            softly.then(refreshTokenJpaRepository.findAll()).hasSize(1);
            softly.then(originalToken.isUsed()).isFalse();
            softly.then(originalToken.isRevoked()).isFalse();
        });
    }
}
