package site.omagotchi.identityservice.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class AccountTest {

    @Test
    @DisplayName("가입 정보 정규화·기본 권한 및 상태")
    void registersAccount() {
        // Given
        String email = "  USER@Example.COM  ";
        String name = "  홍길동  ";

        // When
        Account account = Account.register(
                email,
                "encoded-password",
                name
        );

        // Then
        thenSoftly(softly -> {
            softly.then(account.getEmail()).isEqualTo("user@example.com");
            softly.then(account.getName()).isEqualTo("홍길동");
            softly.then(account.getGlobalRole()).isEqualTo(GlobalRole.USER);
            softly.then(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(Account.isRegistrationEmailValid("user@example.com")).isTrue();
            softly.then(Account.isRegistrationEmailValid(null)).isFalse();
            softly.then(Account.isRegistrationNameValid("사용자")).isTrue();
            softly.then(Account.isRegistrationNameValid(" ")).isFalse();
        });
    }

    @Test
    @DisplayName("이메일 최소 구조와 최종 생성 방어")
    void validatesEmailStructure() {
        // Given
        String passwordHash = "encoded-password";
        String name = "사용자";
        String maximumLengthEmail = "a".repeat(64)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(61);
        String tooLongEmail = maximumLengthEmail + "d";

        // When
        Throwable thrown = catchThrowable(() -> Account.register(
                "not-an-email",
                passwordHash,
                name
        ));

        // Then
        thenSoftly(softly -> {
            softly.then(Account.isRegistrationEmailValid("user+tag@example.co.kr")).isTrue();
            softly.then(Account.isRegistrationEmailValid("user @example.com")).isFalse();
            softly.then(Account.isRegistrationEmailValid("@example.com")).isFalse();
            softly.then(Account.isRegistrationEmailValid("user@")).isFalse();
            softly.then(Account.isRegistrationEmailValid("user@@example.com")).isFalse();
            softly.then(Account.isRegistrationEmailValid(".user@example.com")).isFalse();
            softly.then(Account.isRegistrationEmailValid("user@.")).isFalse();
            softly.then(Account.isRegistrationEmailValid(maximumLengthEmail)).isTrue();
            softly.then(Account.isRegistrationEmailValid(tooLongEmail)).isFalse();
            softly.then(thrown).isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("비밀번호 길이·문자 허용 정책")
    void validatesPasswordLengthAndCharacters() {
        // Given
        String validPassword = "가나다라마바사 아자차카타파하";
        String tooShortPassword = "가".repeat(14);
        String passwordWithControlCharacter = "가".repeat(14) + "\n";

        // Then
        thenSoftly(softly -> {
            softly.then(PasswordPolicy.isSatisfiedBy(validPassword)).isTrue();
            softly.then(PasswordPolicy.isSatisfiedBy(null)).isFalse();
            softly.then(PasswordPolicy.isSatisfiedBy(tooShortPassword)).isFalse();
            softly.then(PasswordPolicy.isSatisfiedBy(passwordWithControlCharacter)).isFalse();
        });
    }

    @Test
    @DisplayName("UTF-8 72바이트 초과 입력 거부")
    void rejectsPasswordOverMaximumUtf8Bytes() {
        // Given
        String password = "가".repeat(24) + "a1";

        // Then
        then(PasswordPolicy.isSatisfiedBy(password)).isFalse();
    }
}
