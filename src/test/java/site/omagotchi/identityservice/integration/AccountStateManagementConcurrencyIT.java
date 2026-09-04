package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.accountstate.application.AdminAccountStatus;
import site.omagotchi.identityservice.accountstate.application.AdminAccountStatusChangeService;
import site.omagotchi.identityservice.accountstate.application.SelfAccountWithdrawalService;
import site.omagotchi.identityservice.accountstate.infrastructure.AccountStatusChangeAuditJpaRepository;
import site.omagotchi.identityservice.auth.application.AuthErrorCode;
import site.omagotchi.identityservice.auth.application.AuthenticationService;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import site.omagotchi.identityservice.global.exception.BusinessException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.BDDAssertions.*;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class AccountStateManagementConcurrencyIT extends BaseIntegrationTest {

    private static final String PASSWORD = "password-passphrase";
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LOCK_CHECK_TIMEOUT = Duration.ofMillis(100);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private AccountStatusChangeAuditJpaRepository auditJpaRepository;

    @Autowired
    private AdminAccountStatusChangeService adminAccountStatusChangeService;

    @Autowired
    private SelfAccountWithdrawalService selfAccountWithdrawalService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private AuthApiTestClient api;
    private AccountStateTestFixture fixture;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        fixture = new AccountStateTestFixture(jdbcTemplate);
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        awaitExecutorTermination();
        cleanDatabase();
    }

    @Test
    @DisplayName("두 SYSTEM_ADMIN의 교차 비활성화에도 한 명 이상 유지")
    void preservesAdministratorDuringConcurrentCrossDisable() throws Exception {
        // Given: 교차 비활성화 경쟁 조건
        UUID first = createAdministrator("first-admin@example.com");
        UUID second = createAdministrator("second-admin@example.com");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Throwable> firstRequest = executor.submit(() -> runTogether(
                ready,
                start,
                () -> adminAccountStatusChangeService.changeStatus(
                        first,
                        second,
                        AdminAccountStatus.DISABLED,
                        "동시 교차 비활성화"
                )
        ));
        Future<Throwable> secondRequest = executor.submit(() -> runTogether(
                ready,
                start,
                () -> adminAccountStatusChangeService.changeStatus(
                        second,
                        first,
                        AdminAccountStatus.DISABLED,
                        "동시 교차 비활성화"
                )
        ));
        await(ready);

        // When: 두 비활성화 요청의 동시 시작
        start.countDown();
        List<Throwable> outcomes = Arrays.asList(
                get(firstRequest),
                get(secondRequest)
        );

        // Then: 단일 성공과 관리자 한 명 유지
        List<Throwable> failures = outcomes.stream().filter(Objects::nonNull).toList();
        thenSoftly(softly -> {
            softly.then(failures).hasSize(1);
            softly.then(failures.getFirst())
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            softly.then(exception.getErrorCode())
                                    .isEqualTo(AccountErrorCode.ADMIN_OPERATION_NOT_ALLOWED)
                    );
            softly.then(usableAdministratorCount()).isEqualTo(1);
            softly.then(auditJpaRepository.count()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("두 SYSTEM_ADMIN의 동시 본인 탈퇴에도 마지막 관리자 보존")
    void preservesAdministratorDuringConcurrentWithdrawals() throws Exception {
        // Given: 두 탈퇴 요청의 동시 시작 경쟁 조건
        UUID first = createAdministrator("first-withdraw-admin@example.com");
        UUID second = createAdministrator("second-withdraw-admin@example.com");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Throwable> firstRequest = executor.submit(() -> runTogether(
                ready,
                start,
                () -> selfAccountWithdrawalService.withdraw(first, PASSWORD)
        ));
        Future<Throwable> secondRequest = executor.submit(() -> runTogether(
                ready,
                start,
                () -> selfAccountWithdrawalService.withdraw(second, PASSWORD)
        ));
        await(ready);

        // When: 두 탈퇴 요청의 동시 시작
        start.countDown();
        List<Throwable> outcomes = Arrays.asList(
                get(firstRequest),
                get(secondRequest)
        );

        // Then: 단일 탈퇴와 관리자 한 명 유지
        List<Throwable> failures = outcomes.stream().filter(Objects::nonNull).toList();
        thenSoftly(softly -> {
            softly.then(failures).hasSize(1);
            softly.then(failures.getFirst())
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            softly.then(exception.getErrorCode())
                                    .isEqualTo(AccountErrorCode.LAST_SYSTEM_ADMIN)
                    );
            softly.then(usableAdministratorCount()).isEqualTo(1);
            softly.then(auditJpaRepository.count()).isZero();
            softly.then(refreshTokenJpaRepository.findAll()).hasSize(2);
            softly.then(refreshTokenJpaRepository.findAll())
                    .filteredOn(RefreshToken::isRevoked)
                    .hasSize(1);
            softly.then(refreshTokenJpaRepository.findAll())
                    .filteredOn(token -> !token.isRevoked())
                    .hasSize(1);
        });
    }

    @Test
    @DisplayName("SYSTEM_ADMIN 보호 행 잠금은 일반 USER 본인 탈퇴를 막지 않음")
    void ordinaryWithdrawalDoesNotWaitForAdministratorGuard() throws Exception {
        // Given: 관리자 보호 행이 점유된 일반 USER 탈퇴 조건
        UUID userId = api.signupSuccessfully("independent-user@example.com");
        api.loginSuccessfully("independent-user@example.com", PASSWORD);
        CountDownLatch guardLocked = new CountDownLatch(1);
        CountDownLatch releaseGuard = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<?> heldGuardLock = executor.submit(() ->
                transaction.executeWithoutResult(status -> {
                    lockSystemAdministratorGuard();
                    guardLocked.countDown();
                    await(releaseGuard);
                })
        );
        await(guardLocked);

        // When: 보호 행과 무관한 일반 USER 탈퇴
        Future<Throwable> withdrawal = executor.submit(() -> catchThrowable(() ->
                selfAccountWithdrawalService.withdraw(userId, PASSWORD)
        ));

        // Then: 보호 행 해제 전 탈퇴 완료
        try {
            Throwable failure = get(withdrawal);
            then(failure).isNull();
        } finally {
            releaseGuard.countDown();
        }
        heldGuardLock.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("일반 USER 비활성화는 SYSTEM_ADMIN 보호 행을 기다리지 않음")
    void ordinaryUserDisableDoesNotWaitForAdministratorGuard() throws Exception {
        // Given: 관리자 보호 행이 점유된 일반 USER 비활성화 조건
        UUID administratorId = createAdministrator("ordinary-disable-admin@example.com");
        UUID targetId = api.signupSuccessfully("ordinary-disable-target@example.com");
        CountDownLatch guardLocked = new CountDownLatch(1);
        CountDownLatch releaseGuard = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<?> heldGuardLock = executor.submit(() ->
                transaction.executeWithoutResult(status -> {
                    lockSystemAdministratorGuard();
                    guardLocked.countDown();
                    await(releaseGuard);
                })
        );
        await(guardLocked);

        // When: 보호 행과 무관한 일반 USER 비활성화
        Future<Throwable> statusChange = executor.submit(() -> catchThrowable(() ->
                adminAccountStatusChangeService.changeStatus(
                        administratorId,
                        targetId,
                        AdminAccountStatus.DISABLED,
                        "일반 사용자 비활성화"
                )
        ));

        // Then: 보호 행 해제 전 비활성화 완료
        try {
            Throwable failure = get(statusChange);
            then(failure).isNull();
            then(auditJpaRepository.count()).isEqualTo(1);
        } finally {
            releaseGuard.countDown();
        }
        heldGuardLock.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("Refresh 회전이 먼저면 회전된 새 Token까지 본인 탈퇴에서 폐기")
    void serializesRefreshRotationAndWithdrawal() throws Exception {
        // Given: Refresh Token 회전이 계정 행을 먼저 잠그는 경쟁 조건
        UUID accountId = api.signupSuccessfully("refresh-race-withdrawal@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "refresh-race-withdrawal@example.com",
                PASSWORD
        );
        CountDownLatch rotationFinished = new CountDownLatch(1);
        CountDownLatch allowRotationCommit = new CountDownLatch(1);
        CountDownLatch withdrawalStarted = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<TokenIssueResult> rotation = executor.submit(() ->
                transaction.execute(status -> {
                    TokenIssueResult result = authenticationService.refresh(
                            login.refreshToken()
                    );
                    rotationFinished.countDown();
                    await(allowRotationCommit);
                    return result;
                })
        );
        await(rotationFinished);

        // When: 회전 트랜잭션 완료 전 본인 탈퇴 요청
        Future<Throwable> withdrawal = executor.submit(() -> {
            withdrawalStarted.countDown();
            return catchThrowable(() ->
                    selfAccountWithdrawalService.withdraw(accountId, PASSWORD)
            );
        });
        await(withdrawalStarted);

        // Then: 회전 완료 전 대기와 완료 후 전체 Refresh Session 폐기
        try {
            thenThrownBy(() -> withdrawal.get(
                    LOCK_CHECK_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
            )).isInstanceOf(TimeoutException.class);
        } finally {
            allowRotationCommit.countDown();
        }
        TokenIssueResult issuedToken = rotation.get(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        then(get(withdrawal)).isNull();

        then(refreshTokenJpaRepository.findAll()).hasSize(2).allSatisfy(token -> {
            then(token.isRevoked()).isTrue();
            then(token.getRevocationReason())
                    .isEqualTo(RefreshTokenRevocationReason.ACCOUNT_WITHDRAWN);
        });
        thenThrownBy(() -> authenticationService.refresh(issuedToken.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    private UUID createAdministrator(String email) throws Exception {
        UUID accountId = api.signupSuccessfully(email);
        fixture.changeGlobalRole(accountId, GlobalRole.SYSTEM_ADMIN);
        api.loginSuccessfully(email, PASSWORD);
        return accountId;
    }

    private Throwable runTogether(
            CountDownLatch ready,
            CountDownLatch start,
            ThrowingOperation operation
    ) {
        ready.countDown();
        await(start);
        return catchThrowable(operation::run);
    }

    private long usableAdministratorCount() {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM identity_service.accounts
                        WHERE global_role = 'SYSTEM_ADMIN'
                          AND status IN ('ACTIVE', 'LOCKED')
                        """,
                Long.class
        );
        return count == null ? 0 : count;
    }

    private Throwable get(Future<Throwable> future) throws Exception {
        return future.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void lockSystemAdministratorGuard() {
        jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM identity_service.system_administrator_guards
                        WHERE id = 1
                        FOR UPDATE
                        """,
                Integer.class
        );
    }

    private void awaitExecutorTermination() {
        try {
            if (!executor.awaitTermination(
                    TEST_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
            )) {
                throw new IllegalStateException("동시성 테스트 작업이 종료되지 않았습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 종료 대기가 중단됐습니다.", exception);
        }
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

    @FunctionalInterface
    private interface ThrowingOperation {

        void run() throws Exception;
    }
}
