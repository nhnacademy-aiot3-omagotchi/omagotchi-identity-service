package site.omagotchi.identityservice.accountstate.domain;

public record AccountStatusChangeReason(String value) {

    public static final int MAX_LENGTH = 500;

    public AccountStatusChangeReason {
        String normalized = value == null ? "" : value.strip();
        int characterCount = normalized.codePointCount(0, normalized.length());
        // 공백·길이·DB 저장 불가 문자 검증
        if (normalized.isBlank()
                || characterCount > MAX_LENGTH
                || normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(
                    "계정 상태 변경 사유는 앞뒤 공백을 제외하고 1~500자이며 NUL 문자를 포함할 수 없습니다."
            );
        }
        value = normalized;
    }
}
