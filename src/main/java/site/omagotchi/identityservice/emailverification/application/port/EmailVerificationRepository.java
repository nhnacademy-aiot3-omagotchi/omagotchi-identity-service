package site.omagotchi.identityservice.emailverification.application.port;

import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationScope;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationRepository {

    EmailVerificationScope createIfAbsentAndLockScope(
            String email,
            EmailVerificationPurpose purpose,
            Instant now
    );

    Optional<EmailVerificationChallenge> lockChallenge(UUID challengeId);

    EmailVerificationChallenge store(EmailVerificationChallenge challenge);
}
