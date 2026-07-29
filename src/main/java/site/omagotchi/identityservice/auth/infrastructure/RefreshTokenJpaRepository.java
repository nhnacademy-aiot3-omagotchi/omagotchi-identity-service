package site.omagotchi.identityservice.auth.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository
        extends JpaRepository<RefreshToken, Long>, RefreshTokenRepository {

    @Override
    default RefreshToken store(RefreshToken refreshToken) {
        return save(refreshToken);
    }

    // 비관적 락: 호출 트랜잭션이 끝날 때까지 동일 Token의 갱신 직렬화
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM RefreshToken token
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> lockByHash(@Param("tokenHash") String tokenHash);

    // Token family 전체를 조회하지 않고 한 번의 UPDATE로 폐기
    // flushAutomatically: 일괄 UPDATE 전, Java 객체 변경을 DB에 반영
    // clearAutomatically: DB와 달라진 기존 JPA Entity를 영속성 컨텍스트에서 제거
    @Override
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
}
