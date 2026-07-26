package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.auth.application.dto.TokenIssueResult;
import site.omagotchi.identityservice.auth.domain.AuthErrorCode;
import site.omagotchi.identityservice.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class RefreshTokenUseCase {

    private final RefreshTokenRotation refreshTokenRotation;

    public TokenIssueResult execute(String rawRefreshToken) {
        return refreshTokenRotation.rotate(rawRefreshToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }
}
