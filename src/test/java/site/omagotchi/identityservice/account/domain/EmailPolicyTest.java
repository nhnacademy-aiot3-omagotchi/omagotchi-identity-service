package site.omagotchi.identityservice.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class EmailPolicyTest {

    @Test
    @DisplayName("이메일 공백 제거와 소문자 정규화")
    void normalizesEmail() {
        // Given
        String email = "  USER@Example.COM  ";

        // When
        String normalizedEmail = EmailPolicy.normalize(email);

        // Then
        then(normalizedEmail).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("이메일 최소 구조와 최대 길이 검증")
    void validatesEmailStructure() {
        // Given
        String maximumLengthEmail = "a".repeat(64)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(61);
        String tooLongEmail = maximumLengthEmail + "d";

        // Then
        thenSoftly(softly -> {
            softly.then(EmailPolicy.isSatisfiedBy("user+tag@example.co.kr")).isTrue();
            softly.then(EmailPolicy.isSatisfiedBy(null)).isFalse();
            softly.then(EmailPolicy.isSatisfiedBy("user @example.com")).isFalse();
            softly.then(EmailPolicy.isSatisfiedBy("@example.com")).isFalse();
            softly.then(EmailPolicy.isSatisfiedBy("user@")).isFalse();
            softly.then(EmailPolicy.isSatisfiedBy("user@@example.com")).isFalse();
            softly.then(EmailPolicy.isSatisfiedBy(".user@example.com")).isFalse();
            softly.then(EmailPolicy.isSatisfiedBy("user@.")).isFalse();
            softly.then(EmailPolicy.isSatisfiedBy(maximumLengthEmail)).isTrue();
            softly.then(EmailPolicy.isSatisfiedBy(tooLongEmail)).isFalse();
        });
    }
}
