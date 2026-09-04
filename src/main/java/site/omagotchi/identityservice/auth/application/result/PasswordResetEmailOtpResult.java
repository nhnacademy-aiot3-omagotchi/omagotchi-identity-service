package site.omagotchi.identityservice.auth.application.result;

import java.util.UUID;

public record PasswordResetEmailOtpResult(
        UUID challengeId,
        long expiresInSeconds
) {
}
