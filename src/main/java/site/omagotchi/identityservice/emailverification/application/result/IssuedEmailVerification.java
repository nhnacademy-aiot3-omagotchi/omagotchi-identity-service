package site.omagotchi.identityservice.emailverification.application.result;

import java.util.UUID;

public record IssuedEmailVerification(
        UUID challengeId,
        long expiresInSeconds
) {
}
