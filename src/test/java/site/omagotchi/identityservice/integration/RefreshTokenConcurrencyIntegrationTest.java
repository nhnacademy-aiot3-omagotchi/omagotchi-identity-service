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
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.AuthErrorCode;
import site.omagotchi.identityservice.auth.application.AuthenticationService;
import site.omagotchi.identityservice.auth.application.RefreshTokenHasher;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
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

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class RefreshTokenConcurrencyIntegrationTest {

    /*
     * 검증 흐름
     * A: Token 갱신 후 DB 행 잠금을 유지한 채 커밋 직전 대기
     * B: 같은 Token으로 갱신을 시도하고 A의 잠금 해제까지 대기
     * A 커밋: B가 이미 사용된 Token을 발견하고 Family 전체 폐기
     */
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
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private AuthenticationService authenticationService;

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
    @DisplayName("동일 Refresh Token 동시 갱신 직렬화와 Family 폐기")
    void serializesConcurrentRefreshAndRevokesFamily() throws Exception {
        // Given
        api.signupSuccessfully("user@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "user@example.com",
                "password-passphrase"
        );
        UUID familyId = storedToken(login.refreshToken()).getFamilyId();

        // 각 요청의 진행 순서를 테스트 코드에서 통제하기 위한 신호
        CountDownLatch firstRotationFinished = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondRotationStarted = new CountDownLatch(1);

        /*
         * 실제 Rotation 트랜잭션 바깥에 테스트용 트랜잭션 추가
         * A가 Rotation을 마친 뒤에도 커밋하지 않고 행 잠금을 유지하기 위한 경계
         */
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // A 요청: 정상 갱신 후 테스트가 허용할 때까지 커밋 보류
        Future<TokenIssueResult> firstRotation = executor.submit(() ->
                transaction.execute(status -> {
                    TokenIssueResult result = authenticationService.refresh(login.refreshToken());
                    firstRotationFinished.countDown();
                    await(allowFirstCommit);
                    return result;
                })
        );

        // A가 새 Token을 만들고 기존 Token의 행 잠금을 확보할 때까지 대기
        await(firstRotationFinished);

        // When
        // B 요청: A와 동일한 원본 Token으로 갱신 시도
        Future<Throwable> secondRotation = executor.submit(() -> {
            secondRotationStarted.countDown();
            return catchThrowable(() -> authenticationService.refresh(login.refreshToken()));
        });
        await(secondRotationStarted);

        // A가 행을 잠근 동안 B가 완료되지 않는 것으로 비관적 락 대기 확인
        thenThrownBy(() -> secondRotation.get(
                LOCK_CHECK_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        )).isInstanceOf(java.util.concurrent.TimeoutException.class);

        // A 커밋과 행 잠금 해제
        allowFirstCommit.countDown();

        // 잠금에서 풀린 B가 사용된 Token을 확인하고 Family 폐기
        TokenIssueResult issuedToken = firstRotation.get(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        Throwable secondFailure = secondRotation.get(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        List<RefreshToken> family = refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getFamilyId().equals(familyId))
                .toList();

        // Then
        thenSoftly(softly -> {
            softly.then(issuedToken).isNotNull();
            softly.then(secondFailure)
                    .isInstanceOf(BusinessException.class)
                    .extracting(throwable ->
                            ((BusinessException) throwable).getErrorCode()
                    )
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
            softly.then(family).hasSize(2);
            softly.then(family)
                    .allSatisfy(token -> {
                        then(token.isRevoked()).isTrue();
                        then(token.getRevocationReason())
                                .isEqualTo(RefreshTokenRevocationReason.REUSE_DETECTED);
                    });
        });

        thenThrownBy(() -> authenticationService.refresh(issuedToken.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(throwable ->
                        ((BusinessException) throwable).getErrorCode()
                )
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    private RefreshToken storedToken(String rawRefreshToken) {
        String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
        return refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getTokenHash().equals(tokenHash))
                .findFirst()
                .orElseThrow();
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
