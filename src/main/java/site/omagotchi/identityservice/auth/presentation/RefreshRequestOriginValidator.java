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
        if (origin == null || !properties.allowedOrigins().contains(origin)) {
            throw new BusinessException(AuthErrorCode.INVALID_REQUEST_ORIGIN);
        }
    }
}

