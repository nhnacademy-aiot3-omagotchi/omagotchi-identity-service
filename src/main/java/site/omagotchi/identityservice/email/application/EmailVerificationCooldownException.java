package site.omagotchi.identityservice.email.application;

import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.exception.RetryAfterException;

public final class EmailVerificationCooldownException extends BusinessException implements RetryAfterException {

    private final long retryAfterSeconds;

    public EmailVerificationCooldownException(long retryAfterSeconds) {
        super(EmailVerificationErrorCode.COOLDOWN_ACTIVE);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    @Override
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
