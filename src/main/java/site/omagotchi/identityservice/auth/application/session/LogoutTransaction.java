package site.omagotchi.identityservice.auth.application.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountSessionStateService;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LogoutTransaction {

    private final AccountSessionStateService accountSessionStateService;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /*
     * Account → RefreshToken 잠금 순서에 의한 Logout 직렬화
     * Refresh Token 부재와 불일치 요청의 멱등 처리
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String refreshTokenHash = refreshTokenHasher.hash(rawRefreshToken);
        Optional<UUID> accountId = refreshTokenRepository.findAccountIdByHash(refreshTokenHash);
        if (accountId.isEmpty()) {
            return;
        }

        accountSessionStateService.lockById(accountId.get());
        Optional<RefreshToken> storedToken = refreshTokenRepository.lockByHash(refreshTokenHash);
        if (storedToken.isEmpty()) {
            return;
        }

        refreshTokenRepository.revokeFamily(
                storedToken.get().getFamilyId(),
                clock.instant(),
                RefreshTokenRevocationReason.LOGOUT
        );
    }
}
