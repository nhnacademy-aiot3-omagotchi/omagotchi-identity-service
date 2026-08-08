package site.omagotchi.identityservice.account.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import site.omagotchi.identityservice.global.exception.ErrorCode;
import site.omagotchi.identityservice.global.exception.ErrorType;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum AccountErrorCode implements ErrorCode {

    INVALID_EMAIL(
            ErrorType.INVALID_INPUT,
            "ACCOUNT_INVALID_EMAIL",
            "이메일은 올바른 주소 형식의 254자 이하여야 합니다."
    ),
    INVALID_PASSWORD(
            ErrorType.INVALID_INPUT,
            "ACCOUNT_INVALID_PASSWORD",
            "비밀번호는 15~64자이며 제어 문자를 포함할 수 없습니다."
    ),
    INVALID_NAME(
            ErrorType.INVALID_INPUT,
            "ACCOUNT_INVALID_NAME",
            "이름은 앞뒤 공백을 제외하고 1~30자여야 합니다."
    ),
    DUPLICATE_EMAIL(
            ErrorType.CONFLICT,
            "ACCOUNT_DUPLICATE_EMAIL",
            "이미 사용 중인 이메일입니다."
    ),
    NOT_FOUND(
            ErrorType.NOT_FOUND,
            "ACCOUNT_NOT_FOUND",
            "계정을 찾을 수 없습니다."
    );

    private final ErrorType type;
    private final String code;
    private final String message;
}
