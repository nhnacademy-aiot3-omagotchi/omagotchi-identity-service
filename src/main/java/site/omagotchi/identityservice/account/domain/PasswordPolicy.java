package site.omagotchi.identityservice.account.domain;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    private static final int MIN_LENGTH = 15;
    private static final int MAX_LENGTH = 64;
    // BCrypt 입력 길이 제약
    private static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    // 문자 조합 대신 길이를 요구하고 제어 문자와 UTF-8 최대 바이트 초과 입력을 거부
    public static boolean isSatisfiedBy(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH
                && !containsIsoControlCharacter(password)
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES;
    }

    // NUL, 탭, 개행 등 ISO 제어 문자가 하나라도 포함됐는지 검사
    private static boolean containsIsoControlCharacter(String password) {
        return password.codePoints().anyMatch(Character::isISOControl);
    }
}
