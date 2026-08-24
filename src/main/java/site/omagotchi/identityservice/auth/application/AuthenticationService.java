package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountAuthenticationService;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AccountAuthenticationService accountAuthenticationService;
    private final LoginTransaction loginTransaction;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRotation refreshTokenRotation;
    private final Clock clock;

    public TokenIssueResult login(String email, String rawPassword) {
        // 로그인 실패 기록 Commit 이후의 공개 인증 실패 변환
        return loginTransaction.login(email, rawPassword)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
    }

    public TokenIssueResult refresh(String rawRefreshToken) {
        // Token family 폐기 트랜잭션 커밋 이후의 인증 실패 변환
        return refreshTokenRotation.rotate(rawRefreshToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            // Refresh Token 부재를 허용하는 멱등 로그아웃
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
