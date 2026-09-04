package site.omagotchi.identityservice.auth.application;

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
    INVALID_PASSWORD_RESET(
            ErrorType.INVALID_INPUT,
            "AUTH_PASSWORD_RESET_INVALID",
            "비밀번호 재설정 정보가 올바르지 않거나 만료되었습니다."
    );

    private final ErrorType type;
    private final String code;
    private final String message;
}
