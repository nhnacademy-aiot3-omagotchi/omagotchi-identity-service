package site.omagotchi.identityservice.email.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.integration.TestJwtConfig;
import site.omagotchi.identityservice.integration.TestcontainersConfig;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class RedisEmailVerificationRepositoryIntegrationTest {

    private static final String EMAIL = "user@example.com";
    private static final String FIRST_CHALLENGE_ID = "first-challenge";
    private static final String SECOND_CHALLENGE_ID = "second-challenge";

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
    @DisplayName("동일 목적과 이메일의 재발송 쿨다운을 TTL 동안 한 번만 획득")
    void acquiresCooldownOnce() {
        boolean first = repository.acquireCooldown(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                Duration.ofSeconds(60)
        );
        boolean second = repository.acquireCooldown(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                Duration.ofSeconds(60)
        );

        thenSoftly(softly -> {
            softly.then(first).isTrue();
            softly.then(second).isFalse();
            softly.then(repository.remainingCooldownSeconds(
                    VerificationPurpose.SIGN_UP,
                    EMAIL
            )).isBetween(1L, 60L);
        });
    }

    @Test
    @DisplayName("사용자 재발급은 기존 OTP를 무효화하고 새 Challenge와 10분 TTL로 교체")
    void replacesActiveChallengeAndResetsTtl() {
        repository.replaceChallenge(
                VerificationPurpose.PASSWORD_RESET,
                EMAIL,
                new OtpChallenge(FIRST_CHALLENGE_ID, "111111"),
                Duration.ofMinutes(2)
        );
        repository.replaceChallenge(
                VerificationPurpose.PASSWORD_RESET,
                EMAIL,
                new OtpChallenge(SECOND_CHALLENGE_ID, "222222"),
                Duration.ofMinutes(10)
        );

        OtpVerificationStatus oldChallenge = repository.verifyAndConsume(
                VerificationPurpose.PASSWORD_RESET,
                EMAIL,
                FIRST_CHALLENGE_ID,
                "111111",
                5
        );
        Long ttl = redisTemplate.getExpire(
                "auth:email:code:PASSWORD_RESET:" + EMAIL,
                TimeUnit.SECONDS
        );
        OtpVerificationStatus newChallenge = repository.verifyAndConsume(
                VerificationPurpose.PASSWORD_RESET,
                EMAIL,
                SECOND_CHALLENGE_ID,
                "222222",
                5
        );
        OtpVerificationStatus consumed = repository.verifyAndConsume(
                VerificationPurpose.PASSWORD_RESET,
                EMAIL,
                SECOND_CHALLENGE_ID,
                "222222",
                5
        );

        thenSoftly(softly -> {
            softly.then(oldChallenge).isEqualTo(OtpVerificationStatus.INVALID);
            softly.then(ttl).isNotNull().isBetween(500L, 600L);
            softly.then(newChallenge).isEqualTo(OtpVerificationStatus.VERIFIED);
            softly.then(consumed).isEqualTo(OtpVerificationStatus.INVALID);
        });
    }

    @Test
    @DisplayName("이전 발송 실패는 재발급된 새 Challenge를 삭제하지 못함")
    void doesNotDeleteReissuedChallengeForStaleDeliveryFailure() {
        repository.replaceChallenge(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                new OtpChallenge(FIRST_CHALLENGE_ID, "111111"),
                Duration.ofMinutes(10)
        );
        repository.replaceChallenge(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                new OtpChallenge(SECOND_CHALLENGE_ID, "222222"),
                Duration.ofMinutes(10)
        );

        boolean staleDeleted = repository.deleteChallengeIfMatches(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                FIRST_CHALLENGE_ID
        );
        OtpVerificationStatus currentChallenge = repository.verifyAndConsume(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                SECOND_CHALLENGE_ID,
                "222222",
                5
        );

        thenSoftly(softly -> {
            softly.then(staleDeleted).isFalse();
            softly.then(currentChallenge).isEqualTo(OtpVerificationStatus.VERIFIED);
        });
    }

    @Test
    @DisplayName("다섯 번째 OTP 실패에서 Challenge를 원자적으로 소진")
    void exhaustsChallengeAtMaximumFailedAttempts() {
        repository.replaceChallenge(
                VerificationPurpose.PASSWORD_CHANGE,
                EMAIL,
                new OtpChallenge(FIRST_CHALLENGE_ID, "111111"),
                Duration.ofMinutes(10)
        );

        for (int attempt = 1; attempt < 5; attempt++) {
            then(repository.verifyAndConsume(
                    VerificationPurpose.PASSWORD_CHANGE,
                    EMAIL,
                    FIRST_CHALLENGE_ID,
                    "999999",
                    5
            )).isEqualTo(OtpVerificationStatus.INVALID);
        }
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

        thenSoftly(softly -> {
            softly.then(fifth).isEqualTo(OtpVerificationStatus.EXHAUSTED);
            softly.then(afterExhaustion).isEqualTo(OtpVerificationStatus.INVALID);
        });
    }

}
