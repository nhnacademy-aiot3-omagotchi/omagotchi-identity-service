package site.omagotchi.identityservice.global.exception;

import lombok.Getter;

import java.util.Objects;

/**
 * 호출자가 처리할 수 없는 외부 의존성 장애를 안정적인 503 계약으로 변환하기 위한 기술 예외다.
 * 원인 예외는 로깅을 위해 보존하고 응답에는 공개하지 않는다.
 */
@Getter
public class DependencyUnavailableException extends RuntimeException {

    private final ErrorCode errorCode;

    public DependencyUnavailableException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode").message(), cause);
        if (errorCode.type() != ErrorType.DEPENDENCY_UNAVAILABLE) {
            throw new IllegalArgumentException("외부 의존성 예외에는 DEPENDENCY_UNAVAILABLE 오류가 필요합니다.");
        }
        this.errorCode = errorCode;
    }
}
