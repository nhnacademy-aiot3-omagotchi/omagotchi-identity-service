package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.emailverification.application.result.PreparedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailDeliveryCooldown;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationScope;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class EmailVerificationDeliveryTransaction {

    private final EmailVerificationRepository repository;
    private final Clock clock;

    /** 전달 결과에 필요한 행을 잠근 뒤 Challenge를 ACCEPTED로 전이한다. */
    @Transactional
    public boolean markAccepted(PreparedEmailVerification prepared) {
        LockedDelivery delivery = lockDelivery(
                prepared,
                "전달 성공 Challenge를 찾을 수 없습니다."
        );

        delivery.challenge().markDeliveryAccepted(delivery.occurredAt());
        return delivery.scope().isCurrentChallenge(prepared.challengeId())
                && delivery.challenge().getStatus() == EmailVerificationStatus.OPEN;
    }

    /** 전달 실패를 기록하고 현재 발급이 소유한 공유 쿨다운을 해제한다. */
    @Transactional
    public void markFailedAndReleaseCooldown(PreparedEmailVerification prepared) {
        LockedDelivery failure = recordDeliveryFailure(prepared);
        if (failure.scope().isCurrentChallenge(prepared.challengeId())) {
            failure.cooldown().releaseIfReservedBy(
                    prepared.challengeId(),
                    failure.occurredAt()
            );
        }
    }

    /** 전달 실패를 기록하되 Provider 요청 제한에 대응해 공유 쿨다운을 유지한다. */
    @Transactional
    public long markFailedKeepingCooldown(PreparedEmailVerification prepared) {
        LockedDelivery failure = recordDeliveryFailure(prepared);
        return failure.cooldown().retryAfterSecondsAt(failure.occurredAt());
    }

    /** 현재 발급이 소유한 공유 쿨다운을 잠금 획득 이후 시각으로 해제한다. */
    @Transactional
    public void releaseCooldown(PreparedEmailVerification prepared) {
        LockedScope locked = lockScope(prepared);
        Instant releasedAt = currentTime();
        if (locked.scope().isCurrentChallenge(prepared.challengeId())) {
            locked.cooldown().releaseIfReservedBy(prepared.challengeId(), releasedAt);
        }
    }

    /** 전달 실패 공통 잠금과 FAILED 상태 전이를 한 번만 수행한다. */
    private LockedDelivery recordDeliveryFailure(PreparedEmailVerification prepared) {
        LockedDelivery failure = lockDelivery(
                prepared,
                "전달 실패 Challenge를 찾을 수 없습니다."
        );
        failure.challenge().markDeliveryFailed(failure.occurredAt());
        return failure;
    }

    /** 공유 쿨다운과 용도별 Scope를 고정 순서로 잠근다. */
    private LockedScope lockScope(PreparedEmailVerification prepared) {
        Instant initializationAt = currentTime();
        EmailDeliveryCooldown cooldown = repository.createIfAbsentAndLockCooldown(
                prepared.email(),
                initializationAt
        );
        EmailVerificationScope scope = repository.createIfAbsentAndLockScope(
                prepared.email(),
                prepared.purpose(),
                initializationAt
        );
        return new LockedScope(cooldown, scope);
    }

    /** 공유 쿨다운·Scope·Challenge를 잠그고 잠금 완료 시각을 확정한다. */
    private LockedDelivery lockDelivery(
            PreparedEmailVerification prepared,
            String missingChallengeMessage
    ) {
        LockedScope locked = lockScope(prepared);
        EmailVerificationChallenge challenge = repository.lockChallenge(prepared.challengeId())
                .orElseThrow(() -> new IllegalStateException(missingChallengeMessage));
        return new LockedDelivery(
                locked.cooldown(),
                locked.scope(),
                challenge,
                currentTime()
        );
    }

    /** 현재 시각을 PostgreSQL 저장 정밀도에 맞춰 반환한다. */
    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 고정 순서로 잠근 공유 쿨다운과 용도별 Scope를 보관한다. */
    private record LockedScope(
            EmailDeliveryCooldown cooldown,
            EmailVerificationScope scope
    ) {
    }

    /** 잠근 전달 상태와 동일 작업에서 사용할 잠금 완료 시각을 보관한다. */
    private record LockedDelivery(
            EmailDeliveryCooldown cooldown,
            EmailVerificationScope scope,
            EmailVerificationChallenge challenge,
            Instant occurredAt
    ) {
    }
}
