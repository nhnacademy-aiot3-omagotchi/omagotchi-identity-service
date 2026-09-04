package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.PasswordResetService;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.application.result.PasswordResetEmailOtpResult;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Import({
        TestcontainersConfig.class,
        TestJwtConfig.class,
        PasswordResetRollbackIT.FailingRevocationConfig.class
})
class PasswordResetRollbackIT {

    private static final String EMAIL = "password-reset-rollback@example.com";
    private static final String CURRENT_PASSWORD = "password-passphrase";
    private static final String NEW_PASSWORD = "new-password-passphrase";
    private static final UUID REFRESH_TOKEN_FAMILY_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000702501"
    );

    @Autowired
    private AccountJpaRepository accountJpaRepository;
    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;
    @Autowired
    private PasswordHasher passwordHasher;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private FailingRefreshTokenRepository failingRefreshTokenRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailVerificationMailSender mailSender;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM identity_service.refresh_tokens");
        jdbcTemplate.update("DELETE FROM identity_service.email_verification_challenges");
        jdbcTemplate.update("DELETE FROM identity_service.email_verification_scopes");
        jdbcTemplate.update("DELETE FROM identity_service.email_delivery_cooldowns");
        jdbcTemplate.update("DELETE FROM identity_service.accounts");
        failingRefreshTokenRepository.reset();
    }

    @Test
    @DisplayName("Session 폐기 실패 시 비밀번호와 Challenge 소비 모두 Rollback")
    void rollsBackPasswordChallengeAndSessionRevocation() {
        // Given
        Account account = accountJpaRepository.saveAndFlush(Account.register(
                EMAIL,
                passwordHasher.hash(CURRENT_PASSWORD),
                "사용자",
                Instant.EPOCH
        ));
        Instant issuedAt = Instant.parse("2026-09-03T00:00:00Z");
        RefreshToken refreshToken = refreshTokenJpaRepository.saveAndFlush(
                RefreshToken.issue(
                        account.getId(),
                        REFRESH_TOKEN_FAMILY_ID,
                        "a".repeat(64),
                        issuedAt.plus(7, ChronoUnit.DAYS),
                        issuedAt
                )
        );
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        PasswordResetEmailOtpResult issued = passwordResetService.issueEmailOtp(EMAIL);
        verify(mailSender).sendVerificationCode(
                eq(issued.challengeId()),
                eq(EMAIL),
                codeCaptor.capture(),
                eq(Duration.ofMinutes(5))
        );

        // When
        Throwable thrown = catchThrowable(() -> passwordResetService.resetPassword(
                EMAIL,
                NEW_PASSWORD,
                issued.challengeId(),
                codeCaptor.getValue()
        ));

        // Then
        Account rolledBackAccount = accountJpaRepository.findById(account.getId()).orElseThrow();
        RefreshToken rolledBackToken = refreshTokenJpaRepository
                .findById(refreshToken.getId())
                .orElseThrow();
        String challengeStatus = jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM identity_service.email_verification_challenges
                WHERE id = ?
                """,
                String.class,
                issued.challengeId()
        );
        thenSoftly(softly -> {
            softly.then(thrown)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("의도한 Refresh Session 폐기 실패");
            softly.then(failingRefreshTokenRepository.wasRevocationAttempted()).isTrue();
            softly.then(passwordHasher.matches(
                    CURRENT_PASSWORD,
                    rolledBackAccount.getPasswordHash()
            )).isTrue();
            softly.then(passwordHasher.matches(
                    NEW_PASSWORD,
                    rolledBackAccount.getPasswordHash()
            )).isFalse();
            softly.then(challengeStatus).isEqualTo("OPEN");
            softly.then(rolledBackToken.isRevoked()).isFalse();
            softly.then(rolledBackToken.getRevocationReason()).isNull();
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingRevocationConfig {

        @Bean
        @Primary
        FailingRefreshTokenRepository failingRefreshTokenRepository(
                @Qualifier("refreshTokenJpaPersistence") RefreshTokenRepository delegate
        ) {
            return new FailingRefreshTokenRepository(delegate);
        }
    }

    static final class FailingRefreshTokenRepository implements RefreshTokenRepository {

        private final RefreshTokenRepository delegate;
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
            int revokedSessions = delegate.revokeAllByAccountId(accountId, revokedAt, reason);
            revocationAttempted = true;
            if (revokedSessions < 1) {
                throw new IllegalStateException("폐기 실패 Test에 필요한 Session이 없습니다.");
            }
            throw new IllegalStateException("의도한 Refresh Session 폐기 실패");
        }

        boolean wasRevocationAttempted() {
            return revocationAttempted;
        }

        void reset() {
            revocationAttempted = false;
        }
    }
}
