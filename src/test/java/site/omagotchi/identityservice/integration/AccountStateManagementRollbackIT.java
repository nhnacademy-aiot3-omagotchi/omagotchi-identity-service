package site.omagotchi.identityservice.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.account.application.port.AccountStatusChangeAuditRepository;
import site.omagotchi.identityservice.account.domain.AccountStatusChangeAudit;
import site.omagotchi.identityservice.account.infrastructure.AccountStatusChangeAuditJpaRepository;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
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
        AccountStateManagementRollbackIT.FailingPersistenceConfig.class
})
class AccountStateManagementRollbackIT {

    private static final String PASSWORD = "password-passphrase";

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
    private FailingRefreshTokenRepository failingRefreshTokenRepository;

    @Autowired
    private FailingAuditRepository failingAuditRepository;

    private AuthApiTestClient api;
    private AccountStateTestFixture fixture;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        fixture = new AccountStateTestFixture(jdbcTemplate);
        failingRefreshTokenRepository.reset();
        failingAuditRepository.reset();
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        failingRefreshTokenRepository.reset();
        failingAuditRepository.reset();
        cleanDatabase();
    }

    @Test
    @DisplayName("SYSTEM_ADMIN 탈퇴 실패 시 상태를 Rollback하고 보호 행 잠금을 해제")
    void rollsBackAdministratorWithdrawalAndReleasesGuard() throws Exception {
        // Given: Refresh Session 폐기 실패 조건의 관리자 탈퇴
        UUID accountId = api.signupSuccessfully("withdrawal-rollback@example.com");
        fixture.changeGlobalRole(accountId, GlobalRole.SYSTEM_ADMIN);
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "withdrawal-rollback@example.com",
                PASSWORD
        );
        UUID remainingAdministratorId = api.signupSuccessfully(
                "remaining-rollback-admin@example.com"
        );
        fixture.changeGlobalRole(
                remainingAdministratorId,
                GlobalRole.SYSTEM_ADMIN
        );
        failingRefreshTokenRepository.failNextAccountRevocation();

        // When: Refresh Session 폐기 중 예외가 발생하는 탈퇴 요청
        api.withdraw(login.accessToken(), PASSWORD)
                .andExpectAll(
                        status().isInternalServerError(),
                        jsonPath("$.code").value("COMMON_INTERNAL_SERVER_ERROR")
                );

        // Then: 계정 상태 변경과 Refresh Session 폐기의 전체 롤백
        thenSoftly(softly -> {
            softly.then(failingRefreshTokenRepository.wasRevocationAttempted()).isTrue();
            softly.then(accountJpaRepository.findById(accountId).orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.ACTIVE);
            softly.then(refreshTokenJpaRepository.findAll())
                    .filteredOn(token -> token.getAccountId().equals(accountId))
                    .hasSize(1)
                    .allSatisfy(token -> {
                        softly.then(token.isRevoked()).isFalse();
                        softly.then(token.getRevocationReason()).isNull();
                    });
            softly.then(auditJpaRepository.count()).isZero();
        });

        // When: 실패 조건 해제 후 탈퇴 재요청
        failingRefreshTokenRepository.reset();
        api.withdraw(login.accessToken(), PASSWORD)
                .andExpect(status().isOk());

        // Then: 롤백 후 보호 행 잠금 해제
        thenSoftly(softly -> {
            softly.then(accountJpaRepository.findById(accountId).orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.WITHDRAWN);
            softly.then(accountJpaRepository.findById(remainingAdministratorId)
                            .orElseThrow()
                            .isActiveSystemAdministrator())
                    .isTrue();
        });
    }

    @Test
    @DisplayName("감사 저장 실패 시 관리자 상태 변경과 Refresh 폐기 전체 Rollback")
    void rollsBackDisableWhenAuditPersistenceFails() throws Exception {
        // Given: 감사 저장 실패 조건의 관리자 비활성화
        UUID administratorId = api.signupSuccessfully("audit-rollback-admin@example.com");
        fixture.changeGlobalRole(administratorId, GlobalRole.SYSTEM_ADMIN);
        AuthApiTestClient.TokenBundle administrator = api.loginSuccessfully(
                "audit-rollback-admin@example.com",
                PASSWORD
        );
        UUID targetId = api.signupSuccessfully("audit-rollback-target@example.com");
        api.loginSuccessfully("audit-rollback-target@example.com", PASSWORD);
        failingAuditRepository.failNextAppend();

        // When: 감사 저장 중 예외가 발생하는 비활성화 요청
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "DISABLED",
                        "감사 저장 실패 검증"
                )
                .andExpectAll(
                        status().isInternalServerError(),
                        jsonPath("$.code").value("COMMON_INTERNAL_SERVER_ERROR")
                );

        // Then: 계정 상태 변경과 Refresh Session 폐기의 전체 롤백
        thenSoftly(softly -> {
            softly.then(failingAuditRepository.wasAppendAttempted()).isTrue();
            softly.then(accountJpaRepository.findById(targetId).orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.ACTIVE);
            softly.then(refreshTokenJpaRepository.findAll())
                    .filteredOn(token -> token.getAccountId().equals(targetId))
                    .hasSize(1)
                    .allSatisfy(token -> {
                        softly.then(token.isRevoked()).isFalse();
                        softly.then(token.getRevocationReason()).isNull();
                    });
            softly.then(auditJpaRepository.count()).isZero();
        });
    }

    private void cleanDatabase() {
        auditJpaRepository.deleteAll();
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingPersistenceConfig {

        @Bean
        @Primary
        FailingRefreshTokenRepository failingRefreshTokenRepository(
                @Qualifier("refreshTokenJpaPersistence") RefreshTokenRepository delegate
        ) {
            return new FailingRefreshTokenRepository(delegate);
        }

        @Bean
        @Primary
        FailingAuditRepository failingAuditRepository(
                @Qualifier("accountStatusChangeAuditJpaPersistence")
                AccountStatusChangeAuditRepository delegate,
                EntityManager entityManager
        ) {
            return new FailingAuditRepository(delegate, entityManager);
        }
    }

    static final class FailingRefreshTokenRepository implements RefreshTokenRepository {

        private final RefreshTokenRepository delegate;
        private boolean failNextAccountRevocation;
        private boolean revocationAttempted;

        private FailingRefreshTokenRepository(RefreshTokenRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public RefreshToken store(RefreshToken refreshToken) {
            return delegate.store(refreshToken);
        }

        @Override
        public Optional<UUID> findAccountIdByHash(String refreshTokenHash) {
            return delegate.findAccountIdByHash(refreshTokenHash);
        }

        @Override
        public Optional<RefreshToken> lockByHash(String refreshTokenHash) {
            return delegate.lockByHash(refreshTokenHash);
        }

        @Override
        public int revokeFamily(
                UUID familyId,
                Instant revokedAt,
                RefreshTokenRevocationReason reason
        ) {
            return delegate.revokeFamily(familyId, revokedAt, reason);
        }

        @Override
        public int revokeAllByAccountId(
                UUID accountId,
                Instant revokedAt,
                RefreshTokenRevocationReason reason
        ) {
            int revoked = delegate.revokeAllByAccountId(accountId, revokedAt, reason);
            revocationAttempted = true;
            if (failNextAccountRevocation) {
                throw new IllegalStateException("의도한 Refresh Session 폐기 실패");
            }
            return revoked;
        }

        void failNextAccountRevocation() {
            failNextAccountRevocation = true;
        }

        boolean wasRevocationAttempted() {
            return revocationAttempted;
        }

        void reset() {
            failNextAccountRevocation = false;
            revocationAttempted = false;
        }
    }

    static final class FailingAuditRepository implements
            AccountStatusChangeAuditRepository {

        private final AccountStatusChangeAuditRepository delegate;
        private final EntityManager entityManager;
        private boolean failNextAppend;
        private boolean appendAttempted;

        private FailingAuditRepository(
                AccountStatusChangeAuditRepository delegate,
                EntityManager entityManager
        ) {
            this.delegate = delegate;
            this.entityManager = entityManager;
        }

        @Override
        public void append(AccountStatusChangeAudit audit) {
            delegate.append(audit);
            appendAttempted = true;
            if (failNextAppend) {
                entityManager.flush();
                throw new IllegalStateException("의도한 감사 저장 실패");
            }
        }

        void failNextAppend() {
            failNextAppend = true;
        }

        boolean wasAppendAttempted() {
            return appendAttempted;
        }

        void reset() {
            failNextAppend = false;
            appendAttempted = false;
        }
    }
}
