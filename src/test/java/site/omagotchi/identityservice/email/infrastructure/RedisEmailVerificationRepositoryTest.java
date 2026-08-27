package site.omagotchi.identityservice.email.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.email.domain.VerifiedEmail;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisEmailVerificationRepositoryTest {

    private static final String TOKEN = "test-verification-token";
    private static final String TOKEN_KEY = "auth:email:verified:" + TOKEN;
    private static final String EMAIL = "user@example.com";
    private static final String COOLDOWN_KEY = "auth:email:cooldown:SIGN_UP:" + EMAIL;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RedisEmailVerificationRepository repository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        repository = new RedisEmailVerificationRepository(redisTemplate, objectMapper);
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

    @Test
    @DisplayName("인증 완료 Token payload를 필드가 명시된 JSON으로 저장")
    void savesVerifiedTokenAsJson() {
        Duration ttl = Duration.ofMinutes(15);
        VerifiedEmail verifiedEmail = new VerifiedEmail(
                EMAIL,
                VerificationPurpose.PASSWORD_RESET
        );
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        repository.saveVerifiedToken(TOKEN, verifiedEmail, ttl);

        verify(valueOperations).set(eq(TOKEN_KEY), payloadCaptor.capture(), eq(ttl));
        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        thenSoftly(softly -> {
            softly.then(payload.get("email").asString()).isEqualTo(EMAIL);
            softly.then(payload.get("purpose").asString()).isEqualTo("PASSWORD_RESET");
        });
    }

    @Test
    @DisplayName("JSON 인증 완료 Token payload를 도메인 값으로 역직렬화")
    void consumesVerifiedTokenFromJson() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.getAndDelete(TOKEN_KEY)).willReturn("""
                {"email":"user@example.com","purpose":"SIGN_UP"}
                """);

        Optional<VerifiedEmail> consumed = repository.consumeVerifiedToken(TOKEN);

        then(consumed).contains(new VerifiedEmail(EMAIL, VerificationPurpose.SIGN_UP));
    }

    @Test
    @DisplayName("필수 필드가 없는 인증 완료 Token payload를 내부 상태 오류로 거절")
    void rejectsInvalidVerifiedTokenJson() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.getAndDelete(TOKEN_KEY)).willReturn("{\"email\":\"\"}");

        Throwable thrown = catchThrowable(() -> repository.consumeVerifiedToken(TOKEN));

        then(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis 이메일 인증 Token 상태가 올바르지 않습니다.");
    }
}
