package site.omagotchi.identityservice.auth.presentation.response;

import site.omagotchi.identityservice.auth.application.result.PasswordResetEmailOtpResult;

import java.util.UUID;

public record PasswordResetEmailOtpResponse(
        UUID challengeId,
        long expiresInSeconds
) {
    public static PasswordResetEmailOtpResponse from(PasswordResetEmailOtpResult issued) {
        return new PasswordResetEmailOtpResponse(
                issued.challengeId(),
                issued.expiresInSeconds()
        );
    }
}
