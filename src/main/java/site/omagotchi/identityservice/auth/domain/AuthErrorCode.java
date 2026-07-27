package site.omagotchi.identityservice.auth.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import site.omagotchi.identityservice.global.exception.ErrorCode;
import site.omagotchi.identityservice.global.exception.ErrorType;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(
            ErrorType.AUTHENTICATION,
            "AUTH_INVALID_CREDENTIALS",
            "로그인 정보가 올바르지 않습니다."
    ),
    INVALID_REFRESH_TOKEN(
            ErrorType.AUTHENTICATION,
            "AUTH_INVALID_REFRESH_TOKEN",
            "Refresh Token이 올바르지 않습니다."
    ),
    INVALID_REQUEST_ORIGIN(
            ErrorType.AUTHORIZATION,
            "AUTH_INVALID_REQUEST_ORIGIN",
            "허용되지 않은 요청 출처입니다."
    );

    private final ErrorType type;
    private final String code;
    private final String message;
}
