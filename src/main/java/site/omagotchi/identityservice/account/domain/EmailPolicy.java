package site.omagotchi.identityservice.account.domain;

import java.util.Arrays;
import java.util.Locale;

public final class EmailPolicy {

    private static final int MAXIMUM_EMAIL_LENGTH = 254;
    private static final int MAXIMUM_LOCAL_PART_LENGTH = 64;
    private static final int MAXIMUM_DOMAIN_LABEL_LENGTH = 63;
    private static final String LOCAL_PART_SPECIAL_CHARACTERS = "!#$%&'*+-/=?^_`{|}~.";

    private EmailPolicy() {
    }

    public static boolean isSatisfiedBy(String email) {
        String normalizedEmail = normalize(email);
        return normalizedEmail.length() <= MAXIMUM_EMAIL_LENGTH
                && hasValidFormat(normalizedEmail);
    }

    public static String normalize(String email) {
        return email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);
    }

    // RFC 전체 검증이 아닌 서비스 허용 이메일의 최소 구조 검증
    private static boolean hasValidFormat(String email) {
        int separatorIndex = email.indexOf('@');
        if (separatorIndex <= 0
                || separatorIndex != email.lastIndexOf('@')
                || separatorIndex == email.length() - 1) {
            return false;
        }

        String localPart = email.substring(0, separatorIndex);
        String domainPart = email.substring(separatorIndex + 1);
        return hasValidLocalPart(localPart)
                && Arrays.stream(domainPart.split("\\.", -1))
                .allMatch(EmailPolicy::hasValidDomainLabel);
    }

    private static boolean hasValidLocalPart(String localPart) {
        return localPart.length() <= MAXIMUM_LOCAL_PART_LENGTH
                && localPart.charAt(0) != '.'
                && localPart.charAt(localPart.length() - 1) != '.'
                && !localPart.contains("..")
                && localPart.chars().allMatch(EmailPolicy::isAllowedLocalPartCharacter);
    }

    private static boolean isAllowedLocalPartCharacter(int character) {
        return Character.isLetterOrDigit(character)
                || LOCAL_PART_SPECIAL_CHARACTERS.indexOf(character) >= 0;
    }

    private static boolean hasValidDomainLabel(String label) {
        return !label.isEmpty()
                && label.length() <= MAXIMUM_DOMAIN_LABEL_LENGTH
                && Character.isLetterOrDigit(label.charAt(0))
                && Character.isLetterOrDigit(label.charAt(label.length() - 1))
                && label.chars().allMatch(
                character -> Character.isLetterOrDigit(character) || character == '-'
        );
    }
}
