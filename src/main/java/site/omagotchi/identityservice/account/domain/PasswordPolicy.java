package site.omagotchi.identityservice.account.domain;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    private static final int MIN_LENGTH = 15;
    private static final int MAX_LENGTH = 64;
    // BCrypt 입력 길이 제약
    private static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    // 문자 조합 대신 길이·제어 문자·BCrypt 최대 입력 길이로 구성한 비밀번호 정책
    public static boolean isSatisfiedBy(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH
                && !containsIsoControlCharacter(password)
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES;
    }

    // NUL·탭·개행을 포함한 ISO 제어 문자 검출
    private static boolean containsIsoControlCharacter(String password) {
        return password.codePoints().anyMatch(Character::isISOControl);
    }
}
