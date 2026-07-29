package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountAuthenticationService;
import site.omagotchi.identityservice.account.application.result.AccountAuthenticationResult;
import site.omagotchi.identityservice.account.application.result.AccountRefreshAccess;
import site.omagotchi.identityservice.auth.application.port.AccessTokenIssuer;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.application.result.IssuedAccessToken;
import site.omagotchi.identityservice.auth.application.result.IssuedRefreshToken;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RefreshTokenRotation {

    private final AccountAuthenticationService accountAuthenticationService;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /*
     * 갱신 실패는 Optional.empty로 반환해 현재 트랜잭션을 정상 커밋
     * family 폐기 커밋 후 AuthenticationService에서 인증 실패 예외로 변환
     */
    @Transactional
    public Optional<TokenIssueResult> rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }

        // 현재 Token 행을 잠가 동일 Token의 동시 갱신 직렬화
        Optional<RefreshToken> storedToken = refreshTokenRepository
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
            // 사용된 Token의 재요청은 탈취 가능성으로 판단해 family 전체 폐기
            refreshTokenRepository.revokeFamily(
                    currentToken.getFamilyId(),
                    now,
                    RefreshTokenRevocationReason.REUSE_DETECTED
            );
            return Optional.empty();
        }

        AccountAuthenticationResult account = accountAuthenticationService
                .getAuthenticationById(currentToken.getAccountId());
        Optional<RefreshTokenRevocationReason> revocationReason =
                findRevocationReason(account.refreshAccess());
        if (revocationReason.isPresent()) {
            refreshTokenRepository.revokeFamily(
                    currentToken.getFamilyId(),
                    now,
                    revocationReason.get()
            );
            return Optional.empty();
        }

        // 기존 Token 소비와 다음 Token 저장을 같은 트랜잭션에서 원자적으로 처리
        currentToken.markUsed(now);
        // 회전해도 현재 로그인 family와 최초 만료 시각 유지
        IssuedRefreshToken nextRefreshToken = refreshTokenIssuer.issue(
                account.accountId(),
                currentToken.getFamilyId(),
                currentToken.getExpiresAt(),
                now
        );
        refreshTokenRepository.store(nextRefreshToken.refreshToken());
        IssuedAccessToken accessToken = accessTokenIssuer.issue(
                account.accountId(),
                account.globalRole()
        );

        return Optional.of(new TokenIssueResult(
                accessToken.value(),
                accessToken.expiresInSeconds(),
                nextRefreshToken.value(),
                currentToken.getExpiresAt()
        ));
    }

    private Optional<RefreshTokenRevocationReason> findRevocationReason(
            AccountRefreshAccess refreshAccess
    ) {
        return switch (refreshAccess) {
            case ALLOWED -> Optional.empty();
            case ACCOUNT_DISABLED -> Optional.of(RefreshTokenRevocationReason.ACCOUNT_DISABLED);
            case ACCOUNT_WITHDRAWN -> Optional.of(RefreshTokenRevocationReason.ACCOUNT_WITHDRAWN);
        };
    }
}
