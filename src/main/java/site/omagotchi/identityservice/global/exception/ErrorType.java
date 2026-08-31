package site.omagotchi.identityservice.global.exception;

public enum ErrorType {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    AUTHENTICATION,
    AUTHORIZATION,
    RATE_LIMIT,
    DEPENDENCY_UNAVAILABLE,
    INTERNAL
}
