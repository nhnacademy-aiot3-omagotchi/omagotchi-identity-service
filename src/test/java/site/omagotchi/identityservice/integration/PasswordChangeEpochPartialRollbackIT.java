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
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.PasswordChangeService;
import site.omagotchi.identityservice.auth.application.port.AuthenticationEpochStore;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class PasswordChangeEpochPartialRollbackIT {

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
    private PasswordChangeService passwordChangeService;

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
    @DisplayName("Epoch 교체 후 외부 DB Transaction 실패 시 안전한 부분 실패")
    void keepsRotatedEpochWhileDatabaseChangesRollBack() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        api.loginSuccessfully("user@example.com", CURRENT_PASSWORD);
        UUID previousEpoch = authenticationEpochStore.find(accountId).orElseThrow();
        String previousPasswordHash = accountJpaRepository.findById(accountId)
                .orElseThrow()
                .getPasswordHash();

        // When
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Throwable thrown = catchThrowable(() -> transaction.executeWithoutResult(status -> {
            passwordChangeService.changePassword(
                    accountId,
                    CURRENT_PASSWORD,
                    NEW_PASSWORD
            );
            throw new IllegalStateException("의도한 외부 Transaction 실패");
        }));

        // Then
        Account account = accountJpaRepository.findById(accountId).orElseThrow();
        RefreshToken refreshToken = refreshTokenJpaRepository.findAll().getFirst();
        UUID currentEpoch = authenticationEpochStore.find(accountId).orElseThrow();

        thenSoftly(softly -> {
            softly.then(thrown)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("의도한 외부 Transaction 실패");
            softly.then(account.getPasswordHash()).isEqualTo(previousPasswordHash);
            softly.then(passwordHasher.matches(CURRENT_PASSWORD, account.getPasswordHash()))
                    .isTrue();
            softly.then(refreshToken.isRevoked()).isFalse();
            softly.then(currentEpoch).isNotEqualTo(previousEpoch);
        });
    }
}
