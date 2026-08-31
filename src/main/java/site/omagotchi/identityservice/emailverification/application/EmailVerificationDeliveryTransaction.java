package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.emailverification.application.result.PreparedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationScope;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationDeliveryTransaction {

    private final EmailVerificationRepository repository;
    private final Clock clock;

    @Transactional
    public void markAccepted(UUID challengeId) {
        repository.lockChallenge(challengeId)
                .ifPresent(challenge -> challenge.markDeliveryAccepted(clock.instant()));
    }

    @Transactional
    public void markFailedAndReleaseCooldown(PreparedEmailVerification prepared) {
        Instant now = clock.instant();
        // 발급과 같은 Scope → Challenge 잠금 순서를 유지한다.
        EmailVerificationScope scope = repository.createIfAbsentAndLockScope(
                prepared.email(),
                prepared.purpose(),
                now
        );
        EmailVerificationChallenge challenge = repository.lockChallenge(prepared.challengeId())
                .orElseThrow(() -> new IllegalStateException("전달 실패 Challenge를 찾을 수 없습니다."));

        challenge.markDeliveryFailed(now);
        scope.releaseCooldownForCurrentChallenge(prepared.challengeId(), now);
    }

    @Transactional
    public void releaseCooldown(PreparedEmailVerification prepared) {
        Instant now = clock.instant();
        EmailVerificationScope scope = repository.createIfAbsentAndLockScope(
                prepared.email(),
                prepared.purpose(),
                now
        );
        scope.releaseCooldownForCurrentChallenge(prepared.challengeId(), now);
    }
}
