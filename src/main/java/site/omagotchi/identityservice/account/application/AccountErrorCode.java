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

    INVALID_SIGNUP_INPUT(
            ErrorType.INVALID_INPUT,
            "ACCOUNT_INVALID_SIGNUP_INPUT",
            "회원가입 정보가 올바르지 않습니다."
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
