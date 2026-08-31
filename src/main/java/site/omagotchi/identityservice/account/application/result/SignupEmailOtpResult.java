package site.omagotchi.identityservice.account.application.result;

import java.util.UUID;

public record SignupEmailOtpResult(
        UUID challengeId,
        long expiresInSeconds
) {
}
