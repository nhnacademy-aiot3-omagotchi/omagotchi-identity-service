package site.omagotchi.identityservice.emailverification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.emailverification.application.result.PreparedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailDeliveryCooldown;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationScope;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EmailVerificationIssuanceTransactionTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final String EMAIL = "member@example.com";
    private static final UUID SCOPE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700701"
    );
    private static final UUID PREVIOUS_CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700702"
    );
    private static final UUID COOLDOWN_CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700703"
    );

    @Mock
    private EmailVerificationRepository repository;
    @Mock
    private VerificationCodeGenerator codeGenerator;
    @Mock
    private VerificationCodeAuthenticator codeAuthenticator;
    @Mock
    private Clock clock;

    private EmailVerificationIssuanceTransaction transaction;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        transaction = new EmailVerificationIssuanceTransaction(
                repository,
                codeGenerator,
                codeAuthenticator,
                new EmailVerificationProperties(
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(1),
                        5,
                        "test-hmac-secret-with-at-least-32-characters"
                ),
                clock
        );
    }

    @Test
    @DisplayName("Scope 잠금 뒤 기존 Challenge를 대체하고 새 Challenge 저장")
    void preparesChallengeAfterScopeLock() {
        // Given
        EmailDeliveryCooldown cooldown = cooldown();
        EmailVerificationScope scope = scope();
        UUID previousId = PREVIOUS_CHALLENGE_ID;
        cooldown.reserve(previousId, NOW.minusSeconds(61), Duration.ofMinutes(1));
        scope.startChallenge(previousId, NOW.minusSeconds(61));
        EmailVerificationChallenge previous = challenge(previousId);
        given(repository.createIfAbsentAndLockCooldown(EMAIL, NOW)).willReturn(cooldown);
        given(repository.createIfAbsentAndLockScope(EMAIL, EmailVerificationPurpose.SIGNUP, NOW))
                .willReturn(scope);
        given(repository.lockChallenge(previousId)).willReturn(Optional.of(previous));
        given(codeGenerator.generate()).willReturn("123456");
        given(codeAuthenticator.encode(any(), any(), any(), any())).willReturn("a".repeat(64));

        // When
        PreparedEmailVerification prepared = transaction.prepare(
                EMAIL,
                EmailVerificationPurpose.SIGNUP
        );

        // Then
        then(prepared.code()).isEqualTo("123456");
        then(prepared.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        then(previous.getStatus()).isEqualTo(EmailVerificationStatus.SUPERSEDED);
        then(scope.getActiveChallengeId()).isEqualTo(prepared.challengeId());
        verify(repository).store(any(EmailVerificationChallenge.class));
    }

    @Test
    @DisplayName("발급 관련 행 잠금 획득 후 읽은 시각부터 Challenge 유효시간 계산")
    void calculatesExpirationFromTimeReadAfterIssuanceLocks() {
        // Given
        Instant cooldownCheckedAt = NOW.plusSeconds(30);
        Instant issuedAt = NOW.plusSeconds(45);
        EmailDeliveryCooldown cooldown = cooldown();
        EmailVerificationScope scope = scope();
        UUID previousId = PREVIOUS_CHALLENGE_ID;
        scope.startChallenge(previousId, NOW.minusSeconds(61));
        EmailVerificationChallenge previous = challenge(previousId);
        given(clock.instant()).willReturn(NOW, cooldownCheckedAt, issuedAt);
        given(repository.createIfAbsentAndLockCooldown(EMAIL, NOW)).willReturn(cooldown);
        given(repository.createIfAbsentAndLockScope(
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                NOW
        )).willReturn(scope);
        given(repository.lockChallenge(previousId)).willReturn(Optional.of(previous));
        given(codeGenerator.generate()).willReturn("123456");
        given(codeAuthenticator.encode(any(), any(), any(), any())).willReturn("a".repeat(64));

        // When
        PreparedEmailVerification prepared = transaction.prepare(
                EMAIL,
                EmailVerificationPurpose.SIGNUP
        );

        // Then
        then(prepared.expiresAt()).isEqualTo(issuedAt.plusSeconds(300));
        then(previous.getUpdatedAt()).isEqualTo(issuedAt);
        var invocationOrder = inOrder(clock, repository);
        invocationOrder.verify(clock).instant();
        invocationOrder.verify(repository).createIfAbsentAndLockCooldown(EMAIL, NOW);
        invocationOrder.verify(repository).createIfAbsentAndLockScope(
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                NOW
        );
        invocationOrder.verify(clock).instant();
        invocationOrder.verify(repository).lockChallenge(previousId);
        invocationOrder.verify(clock).instant();
    }

    @Test
    @DisplayName("쿨다운 중 발급은 Retry-After 정보와 함께 거부")
    void rejectsDuringCooldown() {
        // Given
        EmailDeliveryCooldown cooldown = cooldown();
        EmailVerificationScope scope = scope();
        cooldown.reserve(COOLDOWN_CHALLENGE_ID, NOW.minusSeconds(30), Duration.ofMinutes(1));
        given(repository.createIfAbsentAndLockCooldown(EMAIL, NOW)).willReturn(cooldown);
        given(repository.createIfAbsentAndLockScope(EMAIL, EmailVerificationPurpose.SIGNUP, NOW))
                .willReturn(scope);

        // When
        // Then
        thenThrownBy(() -> transaction.prepare(EMAIL, EmailVerificationPurpose.SIGNUP))
                .isInstanceOfSatisfying(
                        EmailVerificationCooldownException.class,
                        exception -> then(exception.retryAfterSeconds()).isEqualTo(30)
                );
        verify(repository).createIfAbsentAndLockCooldown(EMAIL, NOW);
        verify(repository).createIfAbsentAndLockScope(
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                NOW
        );
        verifyNoMoreInteractions(repository);
    }

    private EmailDeliveryCooldown cooldown() {
        return EmailDeliveryCooldown.create(EMAIL, NOW.minusSeconds(120));
    }

    private EmailVerificationScope scope() {
        return EmailVerificationScope.create(
                SCOPE_ID,
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                NOW.minusSeconds(120)
        );
    }

    private EmailVerificationChallenge challenge(UUID id) {
        return EmailVerificationChallenge.issue(
                id,
                SCOPE_ID,
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                "a".repeat(64),
                NOW.plusSeconds(300),
                NOW.minusSeconds(120)
        );
    }
}
