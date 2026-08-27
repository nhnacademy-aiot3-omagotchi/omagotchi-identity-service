package site.omagotchi.identityservice.email.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisEmailVerificationRepository implements EmailVerificationRepository {

    private static final String KEY_PREFIX = "auth:email:";
    private static final String COOLDOWN_VALUE = "1";
    private static final long REDIS_TTL_KEY_NOT_FOUND = -2L;
    private static final long REDIS_TTL_NO_EXPIRATION = -1L;
    private static final long VERIFIED_RESULT = 1L;
    private static final long EXHAUSTED_RESULT = 2L;

    /**
     * OTP Challenge를 저장/교체하고 실패 횟수를 0으로 초기화한 뒤 만료 시간(TTL)을 원자적으로 설정하는 스크립트
     */
    private static final DefaultRedisScript<Long> REPLACE_CHALLENGE_SCRIPT =
            new DefaultRedisScript<>("""
                    -- OTP Challenge 저장/교체 및 TTL 설정 (반환 코드: 1 = 성공)
                    redis.call('HSET', KEYS[1],
                        'challengeId', ARGV[1],
                        'code', ARGV[2],
                        'failedAttempts', '0')
                    redis.call('PEXPIRE', KEYS[1], ARGV[3])
                    return 1
                    """, Long.class);

    /**
     * OTP Challenge를 검증하고 일치 시 소비(삭제)하거나 실패 횟수를 증가/소진시키는 스크립트
     * 반환 코드:
     * - 0: INVALID (키 없음, challengeId 불일치, 또는 코드 불일치)
     * - 1: VERIFIED (코드 일치 및 소비 완료)
     * - 2: EXHAUSTED (최대 실패 횟수 초과로 인한 챌린지 소진/삭제)
     */
    private static final DefaultRedisScript<Long> VERIFY_AND_CONSUME_SCRIPT =
            new DefaultRedisScript<>("""
                    -- OTP Challenge 검증 및 원자적 소비/실패 처리
                    -- Return codes: 0 = INVALID, 1 = VERIFIED, 2 = EXHAUSTED
                    local storedChallengeId = redis.call('HGET', KEYS[1], 'challengeId')
                    local storedCode = redis.call('HGET', KEYS[1], 'code')
                    if not storedChallengeId or not storedCode then
                        return 0
                    end
                    if storedChallengeId ~= ARGV[1] then
                        return 0
                    end
                    if storedCode == ARGV[2] then
                        redis.call('DEL', KEYS[1])
                        return 1
                    end
                    local failedAttempts = redis.call(
                        'HINCRBY', KEYS[1], 'failedAttempts', 1)
                    if failedAttempts >= tonumber(ARGV[3]) then
                        redis.call('DEL', KEYS[1])
                        return 2
                    end
                    return 0
                    """, Long.class);

    /**
     * 메일 발송 실패 등의 롤백 시, 요청한 challengeId와 현재 저장된 challengeId가 일치할 때만 원자적으로 삭제하는 스크립트
     * 반환 코드:
     * - 1: 일치하여 삭제 성공
     * - 0: challengeId 불일치 또는 키 없음 (삭제 미수행)
     */
    private static final DefaultRedisScript<Long> DELETE_IF_MATCHES_SCRIPT =
            new DefaultRedisScript<>("""
                    -- challengeId 일치 시에만 안전하게 삭제 (반환 코드: 1 = 삭제 성공, 0 = 불일치/미삭제)
                    local storedChallengeId = redis.call('HGET', KEYS[1], 'challengeId')
                    if storedChallengeId == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean acquireCooldown(
            VerificationPurpose purpose,
            String email,
            Duration ttl
    ) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                cooldownKey(purpose, email),
                COOLDOWN_VALUE,
                ttl
        );
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public long remainingCooldownSeconds(VerificationPurpose purpose, String email) {
        Long remainingSecond = redisTemplate.getExpire(
                cooldownKey(purpose, email),
                TimeUnit.SECONDS
        );
        if (remainingSecond == null) {
            throw new IllegalStateException(
                    "Redis Cooldown TTL 조회에 실패했습니다."
            );
        }
        if (remainingSecond == REDIS_TTL_KEY_NOT_FOUND) {
            return 0;
        }
        if (remainingSecond == REDIS_TTL_NO_EXPIRATION) {
            throw new IllegalStateException(
                    "Redis Cooldown Key에 만료 시간이 설정되지 않았습니다."
            );
        }
        return Math.max(1, remainingSecond);
    }

    @Override
    public void replaceChallenge(
            VerificationPurpose purpose,
            String email,
            OtpChallenge challenge,
            Duration ttl
    ) {
        OtpChallenge requiredChallenge = Objects.requireNonNull(challenge, "challenge");
        Long replaced = redisTemplate.execute(
                REPLACE_CHALLENGE_SCRIPT,
                List.of(codeKey(purpose, email)),
                requiredChallenge.challengeId(),
                requiredChallenge.code(),
                Long.toString(Math.max(1, ttl.toMillis()))
        );
        if (!Objects.equals(replaced, 1L)) {
            throw new IllegalStateException("Redis OTP Challenge 교체에 실패했습니다.");
        }
    }

    @Override
    public OtpVerificationStatus verifyAndConsume(
            VerificationPurpose purpose,
            String email,
            String challengeId,
            String verificationCode,
            int maximumAttempts
    ) {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("maximumAttempts는 1 이상이어야 합니다.");
        }
        Long result = redisTemplate.execute(
                VERIFY_AND_CONSUME_SCRIPT,
                List.of(codeKey(purpose, email)),
                challengeId,
                verificationCode,
                Integer.toString(maximumAttempts)
        );
        if (result == null) {
            throw new IllegalStateException("Redis OTP Challenge 검증에 실패했습니다.");
        }
        if (result == VERIFIED_RESULT) {
            return OtpVerificationStatus.VERIFIED;
        }
        if (result == EXHAUSTED_RESULT) {
            return OtpVerificationStatus.EXHAUSTED;
        }
        return OtpVerificationStatus.INVALID;
    }

    @Override
    public boolean deleteChallengeIfMatches(
            VerificationPurpose purpose,
            String email,
            String challengeId
    ) {
        Long deleted = redisTemplate.execute(
                DELETE_IF_MATCHES_SCRIPT,
                List.of(codeKey(purpose, email)),
                challengeId
        );
        return Objects.equals(deleted, 1L);
    }

    private String cooldownKey(VerificationPurpose purpose, String email) {
        return KEY_PREFIX + "cooldown:" + purpose.name() + ":" + email;
    }

    private String codeKey(VerificationPurpose purpose, String email) {
        return KEY_PREFIX + "code:" + purpose.name() + ":" + email;
    }
}
