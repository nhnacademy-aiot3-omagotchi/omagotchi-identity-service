package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountAuthenticationService;
import site.omagotchi.identityservice.account.application.result.AccountAuthenticationResult;
import site.omagotchi.identityservice.auth.application.port.AccessTokenIssuer;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.application.result.IssuedAccessToken;
import site.omagotchi.identityservice.auth.application.result.IssuedRefreshToken;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AccountAuthenticationService accountAuthenticationService;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRotation refreshTokenRotation;
    private final Clock clock;

    @Transactional
    public TokenIssueResult login(String email, String rawPassword) {
        AccountAuthenticationResult account = accountAuthenticationService
                .authenticate(email, rawPassword)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        Instant issuedAt = clock.instant();
        IssuedRefreshToken refreshToken = refreshTokenIssuer.issueNewFamily(
                account.accountId(),
                issuedAt
        );
        refreshTokenRepository.store(refreshToken.refreshToken());

        IssuedAccessToken accessToken = accessTokenIssuer.issue(
                account.accountId(),
                account.globalRole()
        );
        return new TokenIssueResult(
                accessToken.value(),
                accessToken.expiresInSeconds(),
                refreshToken.value(),
                refreshToken.refreshToken().getExpiresAt()
        );
    }

    public TokenIssueResult refresh(String rawRefreshToken) {
        // Rotation의 family 폐기 트랜잭션이 끝난 뒤 인증 실패로 변환
        return refreshTokenRotation.rotate(rawRefreshToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            // Refresh Cookie가 없어도 로그아웃은 성공 처리
            return;
        }

        refreshTokenRepository
                .lockByHash(refreshTokenHasher.hash(rawRefreshToken))
                .ifPresent(refreshToken -> refreshTokenRepository.revokeFamily(
                        refreshToken.getFamilyId(),
                        clock.instant(),
                        RefreshTokenRevocationReason.LOGOUT
                ));
    }
}
