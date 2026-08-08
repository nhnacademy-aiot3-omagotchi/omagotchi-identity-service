package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountAuthenticationService;
import site.omagotchi.identityservice.account.application.result.AccountAuthenticationResult;
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
     * Token 회전·재사용 탐지·family 폐기의 단일 Transaction 경계
     * Optional.empty 반환을 통한 폐기 변경 Commit 이후의 인증 실패 변환
     */
    @Transactional
    public Optional<TokenIssueResult> rotate(String rawRefreshToken) {
        // 빈 원문 Token의 저장소 조회 없는 인증 실패 처리
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }

        // Hash 조회와 현재 Token 행 잠금을 통한 동일 Token의 동시 갱신 직렬화
        Optional<RefreshToken> storedToken = refreshTokenRepository
                .lockByHash(refreshTokenHasher.hash(rawRefreshToken));
        if (storedToken.isEmpty()) {
            // 원문 Token 미보관에 따른 Hash 조회 실패의 인증 실패 처리
            return Optional.empty();
        }

        RefreshToken currentToken = storedToken.get();
        Instant now = clock.instant();

        // 만료·기폐기 Token의 추가 상태 변경 없는 인증 실패 처리
        if (currentToken.isExpiredAt(now) || currentToken.isRevoked()) {
            return Optional.empty();
        }
        if (currentToken.isUsed()) {
            // 사용된 Token 재요청을 탈취 가능성으로 판단한 family 전체 폐기
            refreshTokenRepository.revokeFamily(
                    currentToken.getFamilyId(),
                    now,
                    RefreshTokenRevocationReason.REUSE_DETECTED
            );
            return Optional.empty();
        }

        AccountAuthenticationResult account = accountAuthenticationService
                .getAuthenticationById(currentToken.getAccountId());
        // LOCKED 계정의 기존 로그인 유지와 DISABLED·WITHDRAWN 계정의 갱신 차단 정책
        Optional<RefreshTokenRevocationReason> revocationReason = switch (account.refreshAccess()) {
            case ALLOWED -> Optional.empty();
            case ACCOUNT_DISABLED -> Optional.of(RefreshTokenRevocationReason.ACCOUNT_DISABLED);
            case ACCOUNT_WITHDRAWN -> Optional.of(RefreshTokenRevocationReason.ACCOUNT_WITHDRAWN);
        };
        if (revocationReason.isPresent()) {
            // 갱신 권한을 잃은 계정의 현재 로그인 family 전체 폐기
            refreshTokenRepository.revokeFamily(
                    currentToken.getFamilyId(),
                    now,
                    revocationReason.get()
            );
            return Optional.empty();
        }

        // 기존 Token 소비와 다음 Token 저장의 단일 트랜잭션 처리
        currentToken.markUsed(now);
        // 회전 이후에도 현재 로그인 family와 최초 만료 시각 유지
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

        // Access·Refresh Token과 인증 주체 정보를 묶은 회전 성공 결과 반환
        return Optional.of(new TokenIssueResult(
                account.accountId(),
                account.globalRole(),
                accessToken.value(),
                accessToken.expiresAt(),
                nextRefreshToken.value(),
                currentToken.getExpiresAt()
        ));
    }
}
