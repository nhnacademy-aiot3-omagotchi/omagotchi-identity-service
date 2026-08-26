package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.AfterEach;
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
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.PasswordChangeService;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import site.omagotchi.identityservice.global.exception.BusinessException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class PasswordChangeConcurrencyIT {

    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String FIRST_NEW_PASSWORD = "first-new-password-passphrase";
    private static final String SECOND_NEW_PASSWORD = "second-new-password-passphrase";
    private static final Duration LOCK_CHECK_TIMEOUT = Duration.ofMillis(500);
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

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
    private PasswordChangeService passwordChangeService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private AuthApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("같은 현재 비밀번호를 사용한 동시 변경 직렬화")
    void serializesConcurrentPasswordChanges() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        api.loginSuccessfully("user@example.com", CURRENT_PASSWORD);
        api.loginSuccessfully("user@example.com", CURRENT_PASSWORD);

        CountDownLatch firstChangeFinished = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondChangeStarted = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // 첫 변경이 계정 행 잠금을 유지한 채 커밋 직전 대기
        Future<?> firstChange = executor.submit(() -> transaction.executeWithoutResult(status -> {
            passwordChangeService.changePassword(
                    accountId,
                    CURRENT_PASSWORD,
                    FIRST_NEW_PASSWORD
            );
            firstChangeFinished.countDown();
            await(allowFirstCommit);
        }));
        await(firstChangeFinished);

        // When
        Future<Throwable> secondChange = executor.submit(() -> {
            secondChangeStarted.countDown();
            return catchThrowable(() -> passwordChangeService.changePassword(
                    accountId,
                    CURRENT_PASSWORD,
                    SECOND_NEW_PASSWORD
            ));
        });
        await(secondChangeStarted);

        // 첫 Transaction이 계정 행을 잠근 동안 두 번째 변경이 완료되지 않아야 함
        try {
            thenThrownBy(() -> secondChange.get(
                    LOCK_CHECK_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
            )).isInstanceOf(TimeoutException.class);
        } finally {
            allowFirstCommit.countDown();
        }

        firstChange.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        Throwable secondFailure = secondChange.get(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        Account changedAccount = accountJpaRepository.findById(accountId).orElseThrow();
        List<RefreshToken> tokens = tokensFor(accountId);

        // Then
        then(secondFailure).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.CURRENT_PASSWORD_MISMATCH)
        );
        thenSoftly(softly -> {
            softly.then(passwordHasher.matches(
                    FIRST_NEW_PASSWORD,
                    changedAccount.getPasswordHash()
            )).isTrue();
            softly.then(passwordHasher.matches(
                    SECOND_NEW_PASSWORD,
                    changedAccount.getPasswordHash()
            )).isFalse();
            softly.then(tokens).hasSize(2).allSatisfy(token -> {
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevocationReason())
                        .isEqualTo(RefreshTokenRevocationReason.PASSWORD_CHANGED);
            });
        });
    }

    private List<RefreshToken> tokensFor(UUID accountId) {
        return refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getAccountId().equals(accountId))
                .toList();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과됐습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단됐습니다.", exception);
        }
    }
}
