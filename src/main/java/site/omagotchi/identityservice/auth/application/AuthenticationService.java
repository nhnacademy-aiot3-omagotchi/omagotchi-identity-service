package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
import site.omagotchi.identityservice.auth.application.session.LoginTransaction;
import site.omagotchi.identityservice.auth.application.session.LogoutTransaction;
import site.omagotchi.identityservice.auth.application.session.RefreshTokenRotation;
import site.omagotchi.identityservice.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final LoginTransaction loginTransaction;
    private final RefreshTokenRotation refreshTokenRotation;
    private final LogoutTransaction logoutTransaction;

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

    public void logout(String rawRefreshToken) {
        logoutTransaction.logout(rawRefreshToken);
    }
}
