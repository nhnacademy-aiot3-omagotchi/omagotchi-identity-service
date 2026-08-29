package site.omagotchi.identityservice.auth.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.exception.CommonErrorCode;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisAuthenticationEpochStoreTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000635"
    );
    private static final String KEY = "auth:account:" + ACCOUNT_ID + ":epoch";

    private ValueOperations<String, String> valueOperations;
    private RedisAuthenticationEpochStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        store = new RedisAuthenticationEpochStore(redisTemplate);
    }

    @Test
    @DisplayName("canonical UUID Authentication Epoch 조회")
    void findsCanonicalEpoch() {
        // Given
        UUID epoch = UUID.randomUUID();
        given(valueOperations.get(KEY)).willReturn(epoch.toString());

        // When
        Optional<UUID> result = store.find(ACCOUNT_ID);

        // Then
        then(result).contains(epoch);
    }

    @Test
    @DisplayName("Authentication Epoch Key 미존재 구분")
    void returnsEmptyWhenKeyIsMissing() {
        // Given
        given(valueOperations.get(KEY)).willReturn(null);

        // When
        Optional<UUID> result = store.find(ACCOUNT_ID);

        // Then
        then(result).isEmpty();
    }

    @Test
    @DisplayName("SET NX로 Authentication Epoch 생성")
    void createsEpochWithSetIfAbsent() {
        // Given
        UUID candidate = UUID.randomUUID();
        given(valueOperations.setIfAbsent(KEY, candidate.toString())).willReturn(true);

        // When
        UUID createdEpoch = store.createIfAbsent(ACCOUNT_ID, candidate);

        // Then
        then(createdEpoch).isEqualTo(candidate);
        verify(valueOperations).setIfAbsent(KEY, candidate.toString());
    }

    @Test
    @DisplayName("동시 생성에서 이미 저장된 Authentication Epoch 반환")
    void returnsWinnerOfConcurrentCreation() {
        // Given
        UUID candidate = UUID.randomUUID();
        UUID storedEpoch = UUID.randomUUID();
        given(valueOperations.setIfAbsent(KEY, candidate.toString())).willReturn(false);
        given(valueOperations.get(KEY)).willReturn(storedEpoch.toString());

        // When
        UUID result = store.createIfAbsent(ACCOUNT_ID, candidate);

        // Then
        then(result).isEqualTo(storedEpoch);
    }

    @Test
    @DisplayName("Authentication Epoch를 TTL 없이 교체")
    void replacesEpoch() {
        // Given
        UUID nextEpoch = UUID.randomUUID();

        // When
        store.replace(ACCOUNT_ID, nextEpoch);

        // Then
        verify(valueOperations).set(KEY, nextEpoch.toString());
    }

    @Test
    @DisplayName("비정규 또는 손상된 저장값을 서비스 불가로 변환")
    void rejectsMalformedStoredEpoch() {
        // Given
        given(valueOperations.get(KEY)).willReturn("not-a-canonical-uuid");

        // When
        Throwable thrown = catchThrowable(() -> store.find(ACCOUNT_ID));

        // Then
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE)
        );
    }

    @Test
    @DisplayName("Redis 명령 장애를 서비스 불가로 변환")
    void translatesRedisFailure() {
        // Given
        given(valueOperations.get(KEY)).willThrow(
                new RedisConnectionFailureException("connection failure")
        );

        // When
        Throwable thrown = catchThrowable(() -> store.find(ACCOUNT_ID));

        // Then
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE)
        );
    }
}
