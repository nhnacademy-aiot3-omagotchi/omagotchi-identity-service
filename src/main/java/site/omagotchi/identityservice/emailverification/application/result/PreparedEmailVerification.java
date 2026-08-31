package site.omagotchi.identityservice.emailverification.application.result;

import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;

import java.time.Instant;
import java.util.UUID;

public record PreparedEmailVerification(
        UUID challengeId,
        String email,
        EmailVerificationPurpose purpose,
        String code,
        Instant expiresAt
) {
    @Override
    public String toString() {
        return "PreparedEmailVerification[challengeId=" + challengeId + ", code=[REDACTED]]";
    }
}
