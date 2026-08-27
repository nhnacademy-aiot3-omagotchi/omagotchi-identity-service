package site.omagotchi.identityservice.email.application.port;

import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;

import java.time.Duration;

public interface EmailVerificationRepository {

    boolean acquireCooldown(
            VerificationPurpose purpose,
            String email,
            Duration ttl
    );

    long remainingCooldownSeconds(VerificationPurpose purpose, String email);

    void replaceChallenge(
            VerificationPurpose purpose,
            String email,
            OtpChallenge challenge,
            Duration ttl
    );

    OtpVerificationStatus verifyAndConsume(
            VerificationPurpose purpose,
            String email,
            String challengeId,
            String verificationCode,
            int maximumAttempts
    );

    boolean deleteChallengeIfMatches(
            VerificationPurpose purpose,
            String email,
            String challengeId
    );

}
