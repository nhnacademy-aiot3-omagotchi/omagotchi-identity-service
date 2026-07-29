package site.omagotchi.identityservice.global.exception;

// 외부 실패 계약에 필요한 분류, Code, Message를 제공
public interface ErrorCode {

    ErrorType type();

    String code();

    String message();
}
