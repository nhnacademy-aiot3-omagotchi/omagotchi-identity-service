package site.omagotchi.identityservice.emailverification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;

class VerificationCodeAuthenticatorTest {

    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000701301"
    );

    private final VerificationCodeAuthenticator authenticator =
            new VerificationCodeAuthenticator(new EmailVerificationProperties(
                    Duration.ofMinutes(5),
                    Duration.ofMinutes(1),
                    5,
                    "test-hmac-secret-with-at-least-32-characters"
            ));

    @Test
    @DisplayName("같은 Challenge 문맥과 인증번호의 HMAC만 일치")
    void matchesOnlySameContextAndCode() {
        // Given
        UUID challengeId = CHALLENGE_ID;

        // When
        String mac = authenticator.encode(
                challengeId,
                "member@example.com",
                EmailVerificationPurpose.SIGNUP,
                "123456"
        );

        // Then
        then(mac).hasSize(64);
        then(authenticator.matches(
                mac,
                challengeId,
                "member@example.com",
                EmailVerificationPurpose.SIGNUP,
                "123456"
        )).isTrue();
        then(authenticator.matches(
                mac,
                challengeId,
                "member@example.com",
                EmailVerificationPurpose.SIGNUP,
                "654321"
        )).isFalse();
        then(authenticator.matches(
                mac,
                challengeId,
                "member@example.com",
                EmailVerificationPurpose.PASSWORD_CHANGE,
                "123456"
        )).isFalse();
        then(authenticator.matches(
                mac,
                challengeId,
                "member@example.com",
                EmailVerificationPurpose.PASSWORD_RESET,
                "123456"
        )).isFalse();
    }
}
