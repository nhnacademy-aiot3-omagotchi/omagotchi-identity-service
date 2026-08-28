package site.omagotchi.identityservice.global.exception;

/**
 * HTTP Retry-After 헤더 정보를 제공하는 예외 계약
 */
public interface RetryAfterException {

    long retryAfterSeconds();
}
