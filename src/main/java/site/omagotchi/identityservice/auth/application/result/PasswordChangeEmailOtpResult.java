package site.omagotchi.identityservice.auth.application.result;

import java.util.UUID;

public record PasswordChangeEmailOtpResult(
        UUID challengeId,
        long expiresInSeconds
) {
}
