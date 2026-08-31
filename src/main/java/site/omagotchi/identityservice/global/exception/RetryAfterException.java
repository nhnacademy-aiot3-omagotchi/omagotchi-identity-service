package site.omagotchi.identityservice.global.exception;

public interface RetryAfterException {

    long retryAfterSeconds();
}
