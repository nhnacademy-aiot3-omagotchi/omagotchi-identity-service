package site.omagotchi.identityservice.accountstate.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import site.omagotchi.identityservice.global.exception.ErrorCode;
import site.omagotchi.identityservice.global.exception.ErrorType;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum AccountStateErrorCode implements ErrorCode {

    INVALID_REASON(
            ErrorType.INVALID_INPUT,
            "ACCOUNT_STATUS_CHANGE_INVALID_REASON",
            "계정 상태 변경 사유는 앞뒤 공백을 제외하고 1~500자이며 NUL 문자를 포함할 수 없습니다."
    );

    private final ErrorType type;
    private final String code;
    private final String message;
}
