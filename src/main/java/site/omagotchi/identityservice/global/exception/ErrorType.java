package site.omagotchi.identityservice.global.exception;

public enum ErrorType {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMIT,
    AUTHENTICATION,
    AUTHORIZATION,
    DEPENDENCY_UNAVAILABLE,
    INTERNAL
}
