package site.omagotchi.identityservice.email.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import site.omagotchi.identityservice.email.application.EmailVerificationReservationResult;
import site.omagotchi.identityservice.email.application.port.EmailVerificationStorageException;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;

import java.time.Duration;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisEmailVerificationRepositoryTest {

    private static final String EMAIL = "user@example.com";
    private static final OtpChallenge CHALLENGE =
            new OtpChallenge("challenge-id", "123456");

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisEmailVerificationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisEmailVerificationRepository(redisTemplate);
    }

    @Test
    @DisplayName("Challenge와 쿨다운 원자적 선점 Redis 장애를 저장소 오류로 변환")
    void translatesReservationFailure() {
        // Given
        RedisConnectionFailureException failure = redisFailure();
        givenScriptExecution().willThrow(failure);

        // When
        Throwable thrown = catchThrowable(this::reserveChallenge);

        // Then
        thenStorageFailureWithOriginalCause(thrown, failure);
    }

    @Test
    @DisplayName("원자적 선점 성공 결과를 반환")
    void returnsSuccessfulReservation() {
        // Given
        givenScriptExecution().willReturn(0L);

        // When
        EmailVerificationReservationResult result = reserveChallenge();

        // Then
        then(result).isEqualTo(EmailVerificationReservationResult.acquired());
    }

    @Test
    @DisplayName("쿨다운 값으로 상수가 아닌 Challenge ID를 저장")
    void storesChallengeIdAsCooldownOwner() {
        // Given
        givenScriptExecution().willReturn(0L);
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = redisScriptCaptor();
        ArgumentCaptor<Object[]> argumentsCaptor =
                ArgumentCaptor.forClass(Object[].class);

        // When
        reserveChallenge();

        // Then
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(java.util.List.of(
                        "auth:email:cooldown:SIGN_UP:" + EMAIL,
                        "auth:email:code:SIGN_UP:" + EMAIL
                )),
                argumentsCaptor.capture()
        );
        then(argumentsCaptor.getValue()[0]).isEqualTo(CHALLENGE.challengeId());
        then(scriptCaptor.getValue().getScriptAsString())
                .contains("redis.call('SET', KEYS[1], ARGV[1]");
    }

    @Test
    @DisplayName("기존 쿨다운의 밀리초 TTL을 올림한 초 단위로 반환")
    void returnsRemainingCooldownRoundedUpToSeconds() {
        // Given
        givenScriptExecution().willReturn(42_001L);

        // When
        EmailVerificationReservationResult result = reserveChallenge();

        // Then
        then(result).isEqualTo(EmailVerificationReservationResult.cooldown(43L));
    }

    @Test
    @DisplayName("Cooldown Key에 만료 시간이 없으면 내부 상태 오류로 거절")
    void rejectsCooldownKeyWithoutExpiration() {
        // Given
        givenScriptExecution().willReturn(-1L);

        // When
        Throwable thrown = catchThrowable(this::reserveChallenge);

        // Then
        then(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis Cooldown Key에 만료 시간이 설정되지 않았습니다.");
    }

    @Test
    @DisplayName("선점 스크립트 결과가 없으면 내부 상태 오류로 거절")
    void rejectsNullReservationResult() {
        // Given
        givenScriptExecution().willReturn(null);

        // When
        Throwable thrown = catchThrowable(this::reserveChallenge);

        // Then
        then(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis OTP Challenge 선점에 실패했습니다.");
    }

    @Test
    @DisplayName("OTP 검증 Redis 장애를 원본 원인과 함께 저장소 오류로 변환")
    void translatesChallengeVerificationFailure() {
        // Given
        RedisConnectionFailureException failure = redisFailure();
        givenScriptExecution().willThrow(failure);

        // When
        Throwable thrown = catchThrowable(() -> repository.verifyAndConsume(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE.challengeId(),
                CHALLENGE.code(),
                5
        ));

        // Then
        thenStorageFailureWithOriginalCause(thrown, failure);
    }

    @Test
    @DisplayName("OTP Challenge 조건부 삭제 Redis 장애를 저장소 오류로 변환")
    void translatesChallengeDeletionFailure() {
        // Given
        RedisConnectionFailureException failure = redisFailure();
        givenScriptExecution().willThrow(failure);

        // When
        Throwable thrown = catchThrowable(() -> repository.deleteChallengeIfMatches(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE.challengeId()
        ));

        // Then
        thenStorageFailureWithOriginalCause(thrown, failure);
    }

    @Test
    @DisplayName("Challenge와 쿨다운 조건부 보상 Redis 장애를 저장소 오류로 변환")
    void translatesReservationCompensationFailure() {
        // Given
        RedisConnectionFailureException failure = redisFailure();
        givenScriptExecution().willThrow(failure);

        // When
        Throwable thrown = catchThrowable(() ->
                repository.deleteChallengeAndCooldownIfMatches(
                        VerificationPurpose.SIGN_UP,
                        EMAIL,
                        CHALLENGE.challengeId()
                ));

        // Then
        thenStorageFailureWithOriginalCause(thrown, failure);
    }

    @Test
    @DisplayName("보상 삭제는 Challenge와 쿨다운의 소유권을 각각 비교")
    void compensatesOnlyKeysOwnedByChallengeId() {
        // Given
        givenScriptExecution().willReturn(2L);
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = redisScriptCaptor();

        // When
        repository.deleteChallengeAndCooldownIfMatches(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE.challengeId()
        );

        // Then
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(java.util.List.of(
                        "auth:email:cooldown:SIGN_UP:" + EMAIL,
                        "auth:email:code:SIGN_UP:" + EMAIL
                )),
                eq(CHALLENGE.challengeId())
        );
        then(scriptCaptor.getValue().getScriptAsString())
                .contains(
                        "storedChallengeId == ARGV[1]",
                        "cooldownOwner == ARGV[1]"
                );
    }

    private EmailVerificationReservationResult reserveChallenge() {
        return repository.reserveChallenge(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE,
                Duration.ofMinutes(10),
                Duration.ofMinutes(1)
        );
    }

    private org.mockito.BDDMockito.BDDMyOngoingStubbing<Long> givenScriptExecution() {
        return given(redisTemplate.execute(
                any(),
                anyList(),
                any(Object[].class)
        ));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<RedisScript<Long>> redisScriptCaptor() {
        return ArgumentCaptor.forClass(RedisScript.class);
    }

    private RedisConnectionFailureException redisFailure() {
        return new RedisConnectionFailureException(
                "Redis 연결 실패",
                new IllegalStateException("원본 연결 실패")
        );
    }

    private void thenStorageFailureWithOriginalCause(
            Throwable thrown,
            RedisConnectionFailureException failure
    ) {
        then(thrown)
                .isInstanceOf(EmailVerificationStorageException.class)
                .hasCause(failure);
    }
}
