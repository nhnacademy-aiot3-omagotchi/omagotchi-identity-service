package site.omagotchi.identityservice.email.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.email.application.EmailVerificationReservationResult;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.application.port.EmailVerificationStorageException;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Repository
@RequiredArgsConstructor
public class RedisEmailVerificationRepository implements EmailVerificationRepository {

    private static final String KEY_PREFIX = "auth:email:";
    private static final long RESERVATION_ACQUIRED = 0L;
    private static final long REDIS_TTL_NO_EXPIRATION = -1L;
    private static final long VERIFIED_RESULT = 1L;
    private static final long EXHAUSTED_RESULT = 2L;

    /**
     * 재발송 쿨다운 획득과 OTP Challenge 저장을 하나의 Redis 실행으로 처리하는 스크립트
     * 반환값: 0 = 선점 성공, 양수 = 기존 쿨다운의 남은 밀리초, -1 = TTL 없는 비정상 쿨다운
     */
    private static final DefaultRedisScript<Long> RESERVE_CHALLENGE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('EXISTS', KEYS[1]) == 1 then
                        local remainingCooldownMillis = redis.call('PTTL', KEYS[1])
                        if remainingCooldownMillis < 0 then
                            return -1
                        end
                        return math.max(1, remainingCooldownMillis)
                    end

                    redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[4])
                    redis.call('HSET', KEYS[2],
                        'challengeId', ARGV[1],
                        'code', ARGV[2],
                        'failedAttempts', '0')
                    redis.call('PEXPIRE', KEYS[2], ARGV[3])
                    return 0
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

    /**
     * Executor 접수 실패 시 동일 challengeId가 소유한 Challenge와 쿨다운만 함께 삭제하는 스크립트
     */
    private static final DefaultRedisScript<Long> DELETE_RESERVATION_IF_MATCHES_SCRIPT =
            new DefaultRedisScript<>("""
                    local deleted = 0
                    local storedChallengeId = redis.call('HGET', KEYS[2], 'challengeId')
                    if storedChallengeId == ARGV[1] then
                        deleted = deleted + redis.call('DEL', KEYS[2])
                    end

                    local cooldownOwner = redis.call('GET', KEYS[1])
                    if cooldownOwner == ARGV[1] then
                        deleted = deleted + redis.call('DEL', KEYS[1])
                    end
                    return deleted
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public EmailVerificationReservationResult reserveChallenge(
            VerificationPurpose purpose,
            String email,
            OtpChallenge challenge,
            Duration challengeTtl,
            Duration cooldownTtl
    ) {
        OtpChallenge requiredChallenge = Objects.requireNonNull(challenge, "challenge");
        Long result = executeRedisCommand(() ->
                redisTemplate.execute(
                        RESERVE_CHALLENGE_SCRIPT,
                        List.of(
                                cooldownKey(purpose, email),
                                codeKey(purpose, email)
                        ),
                        requiredChallenge.challengeId(),
                        requiredChallenge.code(),
                        Long.toString(Math.max(1, challengeTtl.toMillis())),
                        Long.toString(Math.max(1, cooldownTtl.toMillis()))
                )
        );
        if (result == null) {
            throw new IllegalStateException("Redis OTP Challenge 선점에 실패했습니다.");
        }
        if (result == RESERVATION_ACQUIRED) {
            return EmailVerificationReservationResult.acquired();
        }
        if (result == REDIS_TTL_NO_EXPIRATION) {
            throw new IllegalStateException(
                    "Redis Cooldown Key에 만료 시간이 설정되지 않았습니다."
            );
        }
        if (result < 1) {
            throw new IllegalStateException("Redis Cooldown TTL 조회에 실패했습니다.");
        }
        return EmailVerificationReservationResult.cooldown(toSecondsCeiling(result));
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
        Long result = executeRedisCommand(() ->
                redisTemplate.execute(
                        VERIFY_AND_CONSUME_SCRIPT,
                        List.of(codeKey(purpose, email)),
                        challengeId,
                        verificationCode,
                        Integer.toString(maximumAttempts)
                )
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
        Long deleted = executeRedisCommand(() ->
                redisTemplate.execute(
                        DELETE_IF_MATCHES_SCRIPT,
                        List.of(codeKey(purpose, email)),
                        challengeId
                )
        );
        return Objects.equals(deleted, 1L);
    }

    @Override
    public void deleteChallengeAndCooldownIfMatches(
            VerificationPurpose purpose,
            String email,
            String challengeId
    ) {
        Long deleted = executeRedisCommand(() ->
                redisTemplate.execute(
                        DELETE_RESERVATION_IF_MATCHES_SCRIPT,
                        List.of(
                                cooldownKey(purpose, email),
                                codeKey(purpose, email)
                        ),
                        challengeId
                )
        );
        if (deleted == null) {
            throw new IllegalStateException("Redis OTP Challenge 보상 정리에 실패했습니다.");
        }
    }

    private long toSecondsCeiling(long milliseconds) {
        long seconds = milliseconds / 1_000L;
        return milliseconds % 1_000L == 0 ? seconds : seconds + 1;
    }

    // Redis 연결·명령 실패를 Port 기술 예외로 변환하고 원본 cause를 보존한다.
    private <T> T executeRedisCommand(Supplier<T> command) {
        try {
            return command.get();
        } catch (DataAccessException exception) {
            throw new EmailVerificationStorageException(exception);
        }
    }

    private String cooldownKey(VerificationPurpose purpose, String email) {
        return KEY_PREFIX + "cooldown:" + purpose.name() + ":" + email;
    }

    private String codeKey(VerificationPurpose purpose, String email) {
        return KEY_PREFIX + "code:" + purpose.name() + ":" + email;
    }
}
