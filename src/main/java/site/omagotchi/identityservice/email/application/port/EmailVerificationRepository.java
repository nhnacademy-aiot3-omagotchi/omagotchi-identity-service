package site.omagotchi.identityservice.email.application.port;

import site.omagotchi.identityservice.email.application.EmailVerificationReservationResult;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;

import java.time.Duration;

public interface EmailVerificationRepository {

    EmailVerificationReservationResult reserveChallenge(
            VerificationPurpose purpose,
            String email,
            OtpChallenge challenge,
            Duration challengeTtl,
            Duration cooldownTtl
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

    void deleteChallengeAndCooldownIfMatches(
            VerificationPurpose purpose,
            String email,
            String challengeId
    );

}
