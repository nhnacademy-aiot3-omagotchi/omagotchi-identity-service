package site.omagotchi.identityservice.auth.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.auth.application.port.AuthenticationEpochStore;
import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.exception.CommonErrorCode;

import java.util.Optional;
import java.util.UUID;

@Component
public class RedisAuthenticationEpochStore implements AuthenticationEpochStore {

    private static final String KEY_PREFIX = "auth:account:";
    private static final String KEY_SUFFIX = ":epoch";

    private final StringRedisTemplate redisTemplate;

    public RedisAuthenticationEpochStore(
            @Qualifier("authenticationEpochRedisTemplate")
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<UUID> find(UUID accountId) {
        String key = KEY_PREFIX + accountId + KEY_SUFFIX;
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key))
                    .map(this::parseEpoch);
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "Authentication Epoch 조회 실패",
                    exception
            );
        }
    }

    @Override
    public UUID createIfAbsent(UUID accountId, UUID candidate) {
        String key = KEY_PREFIX + accountId + KEY_SUFFIX;
        try {
            Boolean created = redisTemplate.opsForValue().setIfAbsent(
                    key,
                    candidate.toString()
            );
            if (Boolean.TRUE.equals(created)) {
                return candidate;
            } else {
                // 동시 생성 경쟁에서 선행 요청이 저장한 Epoch 재사용
                String storedValue = redisTemplate.opsForValue().get(key);
                if (storedValue != null) {
                    return parseEpoch(storedValue);
                }
            }
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "Authentication Epoch 원자 생성 결과 확인 실패"
            );
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "Authentication Epoch 생성 실패",
                    exception
            );
        }
    }

    @Override
    public void replace(UUID accountId, UUID nextEpoch) {
        String key = KEY_PREFIX + accountId + KEY_SUFFIX;
        try {
            redisTemplate.opsForValue().set(key, nextEpoch.toString());
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "Authentication Epoch 교체 실패",
                    exception
            );
        }
    }

    private UUID parseEpoch(String storedValue) {
        try {
            UUID epoch = UUID.fromString(storedValue);
            // 서비스 간 동일한 Canonical UUID 형식 보장
            if (!epoch.toString().equals(storedValue)) {
                throw new BusinessException(
                        CommonErrorCode.SERVICE_UNAVAILABLE,
                        "Authentication Epoch 저장값 형식 오류"
                );
            }
            return epoch;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "Authentication Epoch 저장값 형식 오류",
                    exception
            );
        }
    }

}
