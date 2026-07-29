package site.omagotchi.identityservice.auth.application.port;

import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken store(RefreshToken refreshToken);

    Optional<RefreshToken> lockByHash(String refreshTokenHash);

    int revokeFamily(
            UUID familyId,
            Instant revokedAt,
            RefreshTokenRevocationReason reason
    );
}
