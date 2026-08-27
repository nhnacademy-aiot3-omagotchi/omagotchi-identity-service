package site.omagotchi.identityservice.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RefreshTokenJpaPersistence implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public RefreshToken store(RefreshToken refreshToken) {
        return refreshTokenJpaRepository.save(refreshToken);
    }

    @Override
    public Optional<UUID> findAccountIdByHash(String refreshTokenHash) {
        return refreshTokenJpaRepository.findAccountIdByHash(refreshTokenHash);
    }

    @Override
    public Optional<RefreshToken> lockByHash(String refreshTokenHash) {
        return refreshTokenJpaRepository.lockByHash(refreshTokenHash);
    }

    @Override
    public int revokeFamily(
            UUID familyId,
            Instant revokedAt,
            RefreshTokenRevocationReason reason
    ) {
        return refreshTokenJpaRepository.revokeFamily(familyId, revokedAt, reason);
    }

    @Override
    public int revokeAllByAccountId(
            UUID accountId,
            Instant revokedAt,
            RefreshTokenRevocationReason reason
    ) {
        return refreshTokenJpaRepository.revokeAllByAccountId(accountId, revokedAt, reason);
    }
}
