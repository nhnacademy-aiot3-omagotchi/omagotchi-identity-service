package site.omagotchi.identityservice.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.global.exception.BusinessException;

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
        });
    }

    @Test
    @DisplayName("비밀번호 길이·문자 허용 정책")
    void validatesPasswordLengthAndCharacters() {
        // Given
        String validPassword = "가나다라마바사 아자차카타파하";
        String tooShortPassword = "가".repeat(14);
        String passwordWithControlCharacter = "가".repeat(14) + "\n";

        // When
        Throwable validResult = catchThrowable(() -> PasswordPolicy.validate(validPassword));
        Throwable tooShortResult = catchThrowable(() -> PasswordPolicy.validate(tooShortPassword));
        Throwable controlCharacterResult = catchThrowable(
                () -> PasswordPolicy.validate(passwordWithControlCharacter)
        );

        // Then
        thenSoftly(softly -> {
            softly.then(validResult).isNull();
            softly.then(tooShortResult).isInstanceOf(BusinessException.class);
            softly.then(controlCharacterResult).isInstanceOf(BusinessException.class);
        });
    }

    @Test
    @DisplayName("BCrypt 72바이트 제한")
    void rejectsPasswordOverBcryptLimit() {
        // Given
        String password = "가".repeat(24) + "a1";

        // When
        Throwable thrown = catchThrowable(() -> PasswordPolicy.validate(password));

        // Then
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.INVALID_SIGNUP_INPUT)
        );
    }
}
