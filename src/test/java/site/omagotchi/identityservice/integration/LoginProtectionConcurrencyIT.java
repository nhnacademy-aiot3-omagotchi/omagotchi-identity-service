package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.AuthErrorCode;
import site.omagotchi.identityservice.auth.application.AuthenticationService;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import site.omagotchi.identityservice.global.exception.BusinessException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class LoginProtectionConcurrencyIT extends BaseIntegrationTest {

    private static final int CONCURRENT_ATTEMPTS = 5;
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private AuthenticationService authenticationService;

    private final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);

    private AuthApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("동시 로그인 실패 직렬화로 횟수 유실 없이 잠금")
    void serializesConcurrentLoginFailuresWithoutLostUpdates() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("user@example.com");
        CountDownLatch attemptsReady = new CountDownLatch(CONCURRENT_ATTEMPTS);
        CountDownLatch startAttempts = new CountDownLatch(1);
        List<Future<Throwable>> attempts = new ArrayList<>();

        for (int attempt = 0; attempt < CONCURRENT_ATTEMPTS; attempt++) {
            attempts.add(executor.submit(() -> {
                attemptsReady.countDown();
                await(startAttempts);
                return catchThrowable(() -> authenticationService.login(
                        "user@example.com",
                        "wrong-password1"
                ));
            }));
        }
        await(attemptsReady);

        // When
        startAttempts.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> attempt : attempts) {
            failures.add(attempt.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        }
        Account account = accountJpaRepository.findById(accountId).orElseThrow();

        // Then
        thenSoftly(softly -> {
            softly.then(failures).allSatisfy(failure -> softly.then(failure)
                    .isInstanceOf(BusinessException.class)
                    .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
            softly.then(account.getFailedLoginAttempts())
                    .isEqualTo((short) CONCURRENT_ATTEMPTS);
            softly.then(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
            softly.then(account.getLockedUntil()).isNotNull();
        });
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
