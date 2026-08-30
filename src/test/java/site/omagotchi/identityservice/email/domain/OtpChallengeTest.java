package site.omagotchi.identityservice.email.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class OtpChallengeTest {

    @Test
    @DisplayName("OTP Challenge 문자열 표현에서 모든 필드 원문 마스킹")
    void redactsEveryFieldFromStringRepresentation() {
        // Given
        String challengeId = "challenge-id";
        String verificationCode = "042910";
        OtpChallenge challenge = new OtpChallenge(challengeId, verificationCode);

        // When
        String result = challenge.toString();

        // Then
        then(result)
                .isEqualTo("OtpChallenge[sensitive fields=[REDACTED]]")
                .doesNotContain(challengeId, verificationCode);
    }
}
