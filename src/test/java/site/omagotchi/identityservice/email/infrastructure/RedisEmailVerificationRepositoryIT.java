package site.omagotchi.identityservice.email.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.identityservice.email.application.EmailVerificationReservationResult;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.integration.TestJwtConfig;
import site.omagotchi.identityservice.integration.TestcontainersConfig;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class RedisEmailVerificationRepositoryIT {

    private static final String EMAIL = "user@example.com";
    private static final String FIRST_CHALLENGE_ID = "first-challenge";
    private static final String SECOND_CHALLENGE_ID = "second-challenge";
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration COOLDOWN_TTL = Duration.ofMinutes(1);

    @Autowired
    private RedisEmailVerificationRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("auth:email:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("Challenge 저장과 challengeId 기반 쿨다운 선점을 한 번에 처리")
    void reservesChallengeAndOwnedCooldownAtomically() {
        // When
        EmailVerificationReservationResult first = reserve(
                VerificationPurpose.SIGN_UP,
                FIRST_CHALLENGE_ID,
                "111111"
        );
        EmailVerificationReservationResult second = reserve(
                VerificationPurpose.SIGN_UP,
                SECOND_CHALLENGE_ID,
                "222222"
        );
        String cooldownOwner = redisTemplate.opsForValue().get(
                cooldownKey(VerificationPurpose.SIGN_UP)
        );
        OtpVerificationStatus firstStatus = repository.verifyAndConsume(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                FIRST_CHALLENGE_ID,
                "111111",
                5
        );
        OtpVerificationStatus secondStatus = repository.verifyAndConsume(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                SECOND_CHALLENGE_ID,
                "222222",
                5
        );

        // Then
        thenSoftly(softly -> {
            softly.then(first.reserved()).isTrue();
            softly.then(first.remainingCooldownSeconds()).isZero();
            softly.then(second.reserved()).isFalse();
            softly.then(second.remainingCooldownSeconds()).isBetween(1L, 60L);
            softly.then(cooldownOwner).isEqualTo(FIRST_CHALLENGE_ID);
            softly.then(firstStatus).isEqualTo(OtpVerificationStatus.VERIFIED);
            softly.then(secondStatus).isEqualTo(OtpVerificationStatus.INVALID);
        });
    }

    @Test
    @DisplayName("쿨다운 종료 후 재발급은 기존 OTP를 무효화하고 TTL을 갱신")
    void reissuesChallengeAfterCooldownExpires() {
        // Given
        reserve(
                VerificationPurpose.PASSWORD_RESET,
                FIRST_CHALLENGE_ID,
                "111111"
        );
        redisTemplate.delete(cooldownKey(VerificationPurpose.PASSWORD_RESET));

        // When
        EmailVerificationReservationResult reissued = reserve(
                VerificationPurpose.PASSWORD_RESET,
                SECOND_CHALLENGE_ID,
                "222222"
        );
        OtpVerificationStatus oldChallenge = repository.verifyAndConsume(
                VerificationPurpose.PASSWORD_RESET,
                EMAIL,
                FIRST_CHALLENGE_ID,
                "111111",
                5
        );
        Long ttl = redisTemplate.getExpire(
                codeKey(VerificationPurpose.PASSWORD_RESET),
                TimeUnit.SECONDS
        );
        OtpVerificationStatus newChallenge = repository.verifyAndConsume(
                VerificationPurpose.PASSWORD_RESET,
                EMAIL,
                SECOND_CHALLENGE_ID,
                "222222",
                5
        );

        // Then
        thenSoftly(softly -> {
            softly.then(reissued.reserved()).isTrue();
            softly.then(oldChallenge).isEqualTo(OtpVerificationStatus.INVALID);
            softly.then(ttl).isNotNull().isBetween(500L, 600L);
            softly.then(newChallenge).isEqualTo(OtpVerificationStatus.VERIFIED);
        });
    }

    @Test
    @DisplayName("이전 요청의 늦은 보상은 새 Challenge와 쿨다운을 삭제하지 못함")
    void staleCompensationDoesNotDeleteNewReservation() {
        // Given
        reserve(
                VerificationPurpose.SIGN_UP,
                FIRST_CHALLENGE_ID,
                "111111"
        );
        redisTemplate.delete(cooldownKey(VerificationPurpose.SIGN_UP));
        EmailVerificationReservationResult reissued = reserve(
                VerificationPurpose.SIGN_UP,
                SECOND_CHALLENGE_ID,
                "222222"
        );

        // When
        repository.deleteChallengeAndCooldownIfMatches(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                FIRST_CHALLENGE_ID
        );
        String cooldownOwner = redisTemplate.opsForValue().get(
                cooldownKey(VerificationPurpose.SIGN_UP)
        );
        OtpVerificationStatus currentChallenge = repository.verifyAndConsume(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                SECOND_CHALLENGE_ID,
                "222222",
                5
        );
        EmailVerificationReservationResult third = reserve(
                VerificationPurpose.SIGN_UP,
                "third-challenge",
                "333333"
        );

        // Then
        thenSoftly(softly -> {
            softly.then(reissued.reserved()).isTrue();
            softly.then(cooldownOwner).isEqualTo(SECOND_CHALLENGE_ID);
            softly.then(currentChallenge).isEqualTo(OtpVerificationStatus.VERIFIED);
            softly.then(third.reserved()).isFalse();
        });
    }

    @Test
    @DisplayName("다섯 번째 OTP 실패에서 Challenge를 원자적으로 소진")
    void exhaustsChallengeAtMaximumFailedAttempts() {
        // Given
        reserve(
                VerificationPurpose.PASSWORD_CHANGE,
                FIRST_CHALLENGE_ID,
                "111111"
        );

        // When
        List<OtpVerificationStatus> failuresBeforeExhaustion = IntStream.range(1, 5)
                .mapToObj(attempt -> repository.verifyAndConsume(
                        VerificationPurpose.PASSWORD_CHANGE,
                        EMAIL,
                        FIRST_CHALLENGE_ID,
                        "999999",
                        5
                ))
                .toList();
        OtpVerificationStatus fifth = repository.verifyAndConsume(
                VerificationPurpose.PASSWORD_CHANGE,
                EMAIL,
                FIRST_CHALLENGE_ID,
                "999999",
                5
        );
        OtpVerificationStatus afterExhaustion = repository.verifyAndConsume(
                VerificationPurpose.PASSWORD_CHANGE,
                EMAIL,
                FIRST_CHALLENGE_ID,
                "111111",
                5
        );

        // Then
        thenSoftly(softly -> {
            softly.then(failuresBeforeExhaustion)
                    .containsOnly(OtpVerificationStatus.INVALID);
            softly.then(fifth).isEqualTo(OtpVerificationStatus.EXHAUSTED);
            softly.then(afterExhaustion).isEqualTo(OtpVerificationStatus.INVALID);
        });
    }

    private EmailVerificationReservationResult reserve(
            VerificationPurpose purpose,
            String challengeId,
            String code
    ) {
        return repository.reserveChallenge(
                purpose,
                EMAIL,
                new OtpChallenge(challengeId, code),
                CODE_TTL,
                COOLDOWN_TTL
        );
    }

    private String cooldownKey(VerificationPurpose purpose) {
        return "auth:email:cooldown:" + purpose.name() + ":" + EMAIL;
    }

    private String codeKey(VerificationPurpose purpose) {
        return "auth:email:code:" + purpose.name() + ":" + EMAIL;
    }
}
