package site.omagotchi.identityservice.emailverification.application;

import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Component
public class VerificationCodeAuthenticator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secretKey;

    public VerificationCodeAuthenticator(EmailVerificationProperties properties) {
        this.secretKey = new SecretKeySpec(
                properties.hmacSecret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    public String encode(
            UUID challengeId,
            String email,
            EmailVerificationPurpose purpose,
            String code
    ) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            return HexFormat.of().formatHex(mac.doFinal(payload(
                    challengeId,
                    email,
                    purpose,
                    code
            ).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256을 초기화할 수 없습니다.", exception);
        }
    }

    public boolean matches(
            String expectedMac,
            UUID challengeId,
            String email,
            EmailVerificationPurpose purpose,
            String code
    ) {
        byte[] expected = HexFormat.of().parseHex(Objects.requireNonNull(expectedMac, "expectedMac"));
        byte[] actual = HexFormat.of().parseHex(encode(challengeId, email, purpose, code));
        return MessageDigest.isEqual(expected, actual);
    }

    private String payload(
            UUID challengeId,
            String email,
            EmailVerificationPurpose purpose,
            String code
    ) {
        return Objects.requireNonNull(challengeId, "challengeId") + "\n"
                + Objects.requireNonNull(email, "email") + "\n"
                + Objects.requireNonNull(purpose, "purpose").name() + "\n"
                + Objects.requireNonNull(code, "code");
    }
}
