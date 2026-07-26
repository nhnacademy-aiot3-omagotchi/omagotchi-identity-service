package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenHasher;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenStore;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class LogoutUseCase {

    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    @Transactional
    public void execute(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenStore
                .lockByHash(refreshTokenHasher.hash(rawRefreshToken))
                .ifPresent(refreshToken -> refreshTokenStore.revokeFamily(
                        refreshToken.getFamilyId(),
                        clock.instant(),
                        RefreshTokenRevocationReason.LOGOUT
                ));
    }
}

