package site.omagotchi.identityservice.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST(
            ErrorType.INVALID_INPUT,
            "COMMON_INVALID_REQUEST",
            "요청값이 올바르지 않습니다."
    ),
    MALFORMED_REQUEST(
            ErrorType.INVALID_INPUT,
            "COMMON_MALFORMED_REQUEST",
            "요청 본문을 읽을 수 없습니다."
    );

    private final ErrorType type;
    private final String code;
    private final String message;
}
