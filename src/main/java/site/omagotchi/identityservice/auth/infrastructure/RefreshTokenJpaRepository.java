package site.omagotchi.identityservice.auth.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {

    // Entity 로딩 없이 계정 잠금 대상 식별
    @Query("""
            SELECT token.accountId
            FROM RefreshToken token
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<UUID> findAccountIdByHash(@Param("tokenHash") String tokenHash);

    // 호출 트랜잭션 종료까지 유지되는 비관적 쓰기 락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM RefreshToken token
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> lockByHash(@Param("tokenHash") String tokenHash);

    // Token family 조회 없이 수행하는 단일 일괄 UPDATE
    // flushAutomatically: 일괄 UPDATE 이전의 Entity 변경 반영
    // clearAutomatically: 일괄 UPDATE 이후 낡은 영속성 컨텍스트 제거
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken token
            SET token.revokedAt = :revokedAt,
                token.revocationReason = :reason
            WHERE token.familyId = :familyId
              AND token.revokedAt IS NULL
            """)
    int revokeFamily(
            @Param("familyId") UUID familyId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") RefreshTokenRevocationReason reason
    );

    // 계정 행 선점에 의한 같은 계정의 Token 발급·회전 직렬화
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken token
            SET token.revokedAt = :revokedAt,
                token.revocationReason = :reason
            WHERE token.accountId = :accountId
              AND token.revokedAt IS NULL
            """)
    int revokeAllByAccountId(
            @Param("accountId") UUID accountId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") RefreshTokenRevocationReason reason
    );
}
