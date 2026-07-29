package site.omagotchi.identityservice.global.exception;

import lombok.Getter;

import java.util.Objects;

/**
 * 외부에 공개할 {@link ErrorCode}가 정해진 예상 가능한 실패.
 * Bug, 호출 계약 위반, 내부 불변식 위반을 공통 500 오류로 감싸는 용도로 사용하지 않는다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode").message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode").message(), cause);
        this.errorCode = errorCode;
    }

}
