package site.omagotchi.identityservice.auth.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.auth.domain.AuthErrorCode;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenProperties;
import site.omagotchi.identityservice.global.exception.BusinessException;

@Component
@RequiredArgsConstructor
public class RefreshRequestOriginValidator {

    private final RefreshTokenProperties properties;

    public void validate(String origin) {
        // 브라우저가 Cookie를 자동 첨부하므로 허용된 웹 출처인지 별도 확인
        if (origin == null || !properties.allowedOrigins().contains(origin)) {
            throw new BusinessException(AuthErrorCode.INVALID_REQUEST_ORIGIN);
        }
    }
}
