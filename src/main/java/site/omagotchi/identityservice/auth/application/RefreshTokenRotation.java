package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountReader;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.auth.application.dto.TokenIssueResult;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.AccessTokenIssuer;
import site.omagotchi.identityservice.auth.infrastructure.IssuedAccessToken;
import site.omagotchi.identityservice.auth.infrastructure.IssuedRefreshToken;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenHasher;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenIssuer;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RefreshTokenRotation {

    /*
     * 재사용 감지로 Token family를 폐기한 트랜잭션을 먼저 커밋하고,
     * 호출한 UseCase가 트랜잭션 밖에서 인증 실패를 반환할 수 있도록 분리함
     */
    private final AccountReader accountReader;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    @Transactional
    public Optional<TokenIssueResult> rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }

        Optional<RefreshToken> storedToken = refreshTokenStore
                .lockByHash(refreshTokenHasher.hash(rawRefreshToken));
        if (storedToken.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken currentToken = storedToken.get();
        Instant now = clock.instant();

        if (currentToken.isExpiredAt(now) || currentToken.isRevoked()) {
            return Optional.empty();
        }
        if (currentToken.isUsed()) {
            refreshTokenStore.revokeFamily(
                    currentToken.getFamilyId(),
                    now,
                    RefreshTokenRevocationReason.REUSE_DETECTED
            );
            return Optional.empty();
        }

        Account account = accountReader.readById(currentToken.getAccountId());
        if (!account.isRefreshAllowed()) {
            refreshTokenStore.revokeFamily(
                    currentToken.getFamilyId(),
                    now,
                    revocationReason(account.getStatus())
            );
            return Optional.empty();
        }

        currentToken.markUsed(now);
        IssuedRefreshToken nextRefreshToken = refreshTokenIssuer.issue(
                account.getId(),
                currentToken.getFamilyId(),
                currentToken.getExpiresAt(),
                now
        );
        refreshTokenStore.save(nextRefreshToken.refreshToken());
        IssuedAccessToken accessToken = accessTokenIssuer.issue(account);

        return Optional.of(new TokenIssueResult(
                accessToken.value(),
                accessToken.expiresInSeconds(),
                nextRefreshToken.value(),
                currentToken.getExpiresAt()
        ));
    }

    private RefreshTokenRevocationReason revocationReason(AccountStatus status) {
        if (status == AccountStatus.WITHDRAWN) {
            return RefreshTokenRevocationReason.ACCOUNT_WITHDRAWN;
        }
        return RefreshTokenRevocationReason.ACCOUNT_DISABLED;
    }
}
