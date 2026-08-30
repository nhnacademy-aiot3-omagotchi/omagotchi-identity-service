package site.omagotchi.identityservice.email.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import site.omagotchi.identityservice.global.exception.ErrorCode;
import site.omagotchi.identityservice.global.exception.ErrorType;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum EmailVerificationErrorCode implements ErrorCode {

    INVALID(
            ErrorType.INVALID_INPUT,
            "EMAIL_VERIFICATION_INVALID",
            "인증 코드가 올바르지 않거나 만료되었습니다."
    ),
    COOLDOWN_ACTIVE(
            ErrorType.RATE_LIMIT,
            "EMAIL_VERIFICATION_COOLDOWN_ACTIVE",
            "잠시 후 인증 코드를 다시 요청해 주세요."
    ),
    UNAVAILABLE(
            ErrorType.DEPENDENCY_UNAVAILABLE,
            "EMAIL_VERIFICATION_UNAVAILABLE",
            "이메일 인증 서비스를 일시적으로 사용할 수 없습니다."
    );

    private final ErrorType type;
    private final String code;
    private final String message;
}
