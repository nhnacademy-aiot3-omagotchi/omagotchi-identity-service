package site.omagotchi.identityservice.email.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RedisEmailVerificationRepositoryTest {

    private static final String EMAIL = "user@example.com";
    private static final String COOLDOWN_KEY = "auth:email:cooldown:SIGN_UP:" + EMAIL;

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisEmailVerificationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisEmailVerificationRepository(redisTemplate);
    }

    @Test
    @DisplayName("Cooldown Key의 남은 TTL을 초 단위로 반환")
    void returnsRemainingCooldownSeconds() {
        given(redisTemplate.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).willReturn(42L);

        long remaining = repository.remainingCooldownSeconds(
                VerificationPurpose.SIGN_UP,
                EMAIL
        );

        then(remaining).isEqualTo(42L);
    }

    @Test
    @DisplayName("Cooldown Key가 없으면 남은 TTL을 0으로 반환")
    void returnsZeroWhenCooldownKeyIsMissing() {
        given(redisTemplate.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).willReturn(-2L);

        long remaining = repository.remainingCooldownSeconds(
                VerificationPurpose.SIGN_UP,
                EMAIL
        );

        then(remaining).isZero();
    }

    @Test
    @DisplayName("Cooldown Key에 만료 시간이 없으면 내부 상태 오류로 거절")
    void rejectsCooldownKeyWithoutExpiration() {
        given(redisTemplate.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).willReturn(-1L);

        Throwable thrown = catchThrowable(() -> repository.remainingCooldownSeconds(
                VerificationPurpose.SIGN_UP,
                EMAIL
        ));

        then(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis Cooldown Key에 만료 시간이 설정되지 않았습니다.");
    }

    @Test
    @DisplayName("Cooldown TTL 조회 결과가 없으면 내부 상태 오류로 거절")
    void rejectsNullCooldownTtlResult() {
        given(redisTemplate.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).willReturn(null);

        Throwable thrown = catchThrowable(() -> repository.remainingCooldownSeconds(
                VerificationPurpose.SIGN_UP,
                EMAIL
        ));

        then(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis Cooldown TTL 조회에 실패했습니다.");
    }

}
