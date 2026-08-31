package site.omagotchi.identityservice.emailverification.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import site.omagotchi.identityservice.global.exception.ErrorCode;
import site.omagotchi.identityservice.global.exception.ErrorType;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum EmailVerificationErrorCode implements ErrorCode {

    INVALID_CHALLENGE(
            ErrorType.INVALID_INPUT,
            "EMAIL_VERIFICATION_INVALID_CHALLENGE",
            "이메일 인증 정보가 올바르지 않거나 만료되었습니다."
    ),
    COOLDOWN_ACTIVE(
            ErrorType.RATE_LIMIT,
            "EMAIL_VERIFICATION_COOLDOWN_ACTIVE",
            "이메일 인증번호를 다시 요청하려면 잠시 기다려야 합니다."
    ),
    ISSUE_SUPERSEDED(
            ErrorType.CONFLICT,
            "EMAIL_VERIFICATION_ISSUE_SUPERSEDED",
            "새 인증번호 요청으로 이전 발급 요청이 대체되었습니다."
    ),
    DELIVERY_UNAVAILABLE(
            ErrorType.DEPENDENCY_UNAVAILABLE,
            "EMAIL_VERIFICATION_DELIVERY_UNAVAILABLE",
            "인증 메일을 전송할 수 없습니다. 잠시 후 다시 시도해 주세요."
    );

    private final ErrorType type;
    private final String code;
    private final String message;
}
