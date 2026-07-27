package site.omagotchi.identityservice.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenJpaRepository.save(refreshToken);
    }

    public Optional<RefreshToken> lockByHash(String tokenHash) {
        return refreshTokenJpaRepository.findByTokenHashForUpdate(tokenHash);
    }

    public void revokeFamily(
            UUID familyId,
            Instant revokedAt,
            RefreshTokenRevocationReason reason
    ) {
        refreshTokenJpaRepository.revokeFamily(familyId, revokedAt, reason);
    }
}

