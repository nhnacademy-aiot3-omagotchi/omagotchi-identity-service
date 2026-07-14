package site.omagotchi.identityservice.global.exception;

import lombok.Getter;

import java.util.Objects;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode").message());
        this.errorCode = errorCode;
    }

}
