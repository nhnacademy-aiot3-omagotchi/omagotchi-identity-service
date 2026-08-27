package site.omagotchi.identityservice.email.application;

import site.omagotchi.identityservice.global.exception.BusinessException;

public final class EmailVerificationCooldownException extends BusinessException {

    private final long retryAfterSeconds;

    public EmailVerificationCooldownException(long retryAfterSeconds) {
        super(EmailVerificationErrorCode.COOLDOWN_ACTIVE);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
