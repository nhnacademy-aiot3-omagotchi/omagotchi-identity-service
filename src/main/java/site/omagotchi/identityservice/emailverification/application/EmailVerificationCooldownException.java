package site.omagotchi.identityservice.emailverification.application;

import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.exception.RetryAfterException;

public class EmailVerificationCooldownException
        extends BusinessException implements RetryAfterException {

    private final long retryAfterSeconds;

    public EmailVerificationCooldownException(long retryAfterSeconds) {
        super(EmailVerificationErrorCode.COOLDOWN_ACTIVE);
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("Retry-After는 1초 이상이어야 합니다.");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
