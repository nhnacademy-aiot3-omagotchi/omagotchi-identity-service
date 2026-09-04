package site.omagotchi.identityservice.emailverification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.emailverification.application.result.PreparedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailDeliveryCooldown;
import site.omagotchi.identityservice.emailverification.domain.EmailDeliveryStatus;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationScope;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class EmailVerificationDeliveryTransactionTest {

    private static final Instant INITIALIZATION_AT = Instant.parse("2026-08-30T00:00:00Z");
    private static final Instant LOCKED_AT = INITIALIZATION_AT.plusSeconds(30);
    private static final String EMAIL = "member@example.com";
    private static final UUID SCOPE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000703001"
    );
    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000703002"
    );

    @Mock
    private EmailVerificationRepository repository;
    @Mock
    private Clock clock;

    private EmailVerificationDeliveryTransaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new EmailVerificationDeliveryTransaction(repository, clock);
    }

    @Test
    @DisplayName("잠금 획득 후 시각으로 메일 전달 성공 상태 기록")
    void marksAcceptedAtTimeReadAfterLocks() {
        // Given
        LockedEntities entities = givenLockedEntities();

        // When
        boolean accepted = transaction.markAccepted(entities.prepared());

        // Then
        then(accepted).isTrue();
        then(entities.challenge().getDeliveryStatus())
                .isEqualTo(EmailDeliveryStatus.ACCEPTED);
        then(entities.challenge().getUpdatedAt()).isEqualTo(LOCKED_AT);
        verifyLockAndTimeOrder();
    }

    @Test
    @DisplayName("공통 실패 처리 후 잠금 획득 시각으로 공유 쿨다운 해제")
    void recordsFailureAndReleasesCooldownAtTimeReadAfterLocks() {
        // Given
        LockedEntities entities = givenLockedEntities();

        // When
        transaction.markFailedAndReleaseCooldown(entities.prepared());

        // Then
        then(entities.challenge().getDeliveryStatus())
                .isEqualTo(EmailDeliveryStatus.FAILED);
        then(entities.challenge().getUpdatedAt()).isEqualTo(LOCKED_AT);
        then(entities.cooldown().getNextIssueAt()).isEqualTo(LOCKED_AT);
        verifyLockAndTimeOrder();
    }

    @Test
    @DisplayName("공통 실패 처리 후 잠금 대기 시간을 제외한 남은 쿨다운 반환")
    void recordsFailureAndCalculatesCooldownAfterLocks() {
        // Given
        LockedEntities entities = givenLockedEntities();

        // When
        long retryAfterSeconds = transaction.markFailedKeepingCooldown(entities.prepared());

        // Then
        then(retryAfterSeconds).isEqualTo(30);
        then(entities.challenge().getDeliveryStatus())
                .isEqualTo(EmailDeliveryStatus.FAILED);
        then(entities.challenge().getUpdatedAt()).isEqualTo(LOCKED_AT);
        then(entities.cooldown().getNextIssueAt())
                .isEqualTo(INITIALIZATION_AT.plusSeconds(60));
        verifyLockAndTimeOrder();
    }

    @Test
    @DisplayName("Challenge를 잠그지 않는 보상도 Scope 잠금 이후 시각으로 쿨다운 해제")
    void releasesCooldownAtTimeReadAfterScopeLock() {
        // Given
        LockedEntities entities = givenLockedScope();

        // When
        transaction.releaseCooldown(entities.prepared());

        // Then
        then(entities.cooldown().getNextIssueAt()).isEqualTo(LOCKED_AT);
        InOrder order = inOrder(clock, repository);
        order.verify(clock).instant();
        order.verify(repository).createIfAbsentAndLockCooldown(EMAIL, INITIALIZATION_AT);
        order.verify(repository).createIfAbsentAndLockScope(
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                INITIALIZATION_AT
        );
        order.verify(clock).instant();
    }

    private LockedEntities givenLockedEntities() {
        LockedEntities entities = givenLockedScope();
        given(repository.lockChallenge(CHALLENGE_ID))
                .willReturn(Optional.of(entities.challenge()));
        return entities;
    }

    private LockedEntities givenLockedScope() {
        EmailDeliveryCooldown cooldown = EmailDeliveryCooldown.create(
                EMAIL,
                INITIALIZATION_AT.minusSeconds(120)
        );
        cooldown.reserve(CHALLENGE_ID, INITIALIZATION_AT, Duration.ofMinutes(1));
        EmailVerificationScope scope = EmailVerificationScope.create(
                SCOPE_ID,
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                INITIALIZATION_AT.minusSeconds(120)
        );
        scope.startChallenge(CHALLENGE_ID, INITIALIZATION_AT);
        EmailVerificationChallenge challenge = EmailVerificationChallenge.issue(
                CHALLENGE_ID,
                SCOPE_ID,
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                "a".repeat(64),
                INITIALIZATION_AT.plusSeconds(300),
                INITIALIZATION_AT
        );
        PreparedEmailVerification prepared = new PreparedEmailVerification(
                CHALLENGE_ID,
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                "123456",
                INITIALIZATION_AT.plusSeconds(300)
        );

        given(clock.instant()).willReturn(INITIALIZATION_AT, LOCKED_AT);
        given(repository.createIfAbsentAndLockCooldown(EMAIL, INITIALIZATION_AT))
                .willReturn(cooldown);
        given(repository.createIfAbsentAndLockScope(
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                INITIALIZATION_AT
        )).willReturn(scope);
        return new LockedEntities(prepared, cooldown, challenge);
    }

    private void verifyLockAndTimeOrder() {
        InOrder order = inOrder(clock, repository);
        order.verify(clock).instant();
        order.verify(repository).createIfAbsentAndLockCooldown(EMAIL, INITIALIZATION_AT);
        order.verify(repository).createIfAbsentAndLockScope(
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                INITIALIZATION_AT
        );
        order.verify(repository).lockChallenge(CHALLENGE_ID);
        order.verify(clock).instant();
    }

    private record LockedEntities(
            PreparedEmailVerification prepared,
            EmailDeliveryCooldown cooldown,
            EmailVerificationChallenge challenge
    ) {
    }
}
