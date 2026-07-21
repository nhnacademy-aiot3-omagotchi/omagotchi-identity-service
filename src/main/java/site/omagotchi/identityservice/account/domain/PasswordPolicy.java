package site.omagotchi.identityservice.account.domain;

import site.omagotchi.identityservice.global.exception.BusinessException;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    private static final int MIN_LENGTH = 15;
    private static final int MAX_LENGTH = 64;
    private static final int BCRYPT_MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null
                || password.length() < MIN_LENGTH
                || password.length() > MAX_LENGTH
                || containsIsoControlCharacter(password)
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES
        ) {
            throw new BusinessException(AccountErrorCode.INVALID_SIGNUP_INPUT);
        }
    }

    // NUL, 탭, 개행 등 ISO 제어 문자가 하나라도 포함됐는지 검사
    private static boolean containsIsoControlCharacter(String password) {
        return password.codePoints().anyMatch(Character::isISOControl);
    }
}
