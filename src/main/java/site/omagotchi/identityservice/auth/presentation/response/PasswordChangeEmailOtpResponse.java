package site.omagotchi.identityservice.auth.presentation.response;

import site.omagotchi.identityservice.auth.application.result.PasswordChangeEmailOtpResult;

import java.util.UUID;

public record PasswordChangeEmailOtpResponse(
        UUID challengeId,
        long expiresInSeconds
) {
    public static PasswordChangeEmailOtpResponse from(PasswordChangeEmailOtpResult issued) {
        return new PasswordChangeEmailOtpResponse(
                issued.challengeId(),
                issued.expiresInSeconds()
        );
    }
}
