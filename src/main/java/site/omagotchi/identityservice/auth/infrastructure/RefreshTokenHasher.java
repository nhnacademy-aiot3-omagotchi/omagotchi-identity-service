package site.omagotchi.identityservice.auth.infrastructure;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class RefreshTokenHasher {

    private static final String HASH_ALGORITHM = "SHA-256";

    public RefreshTokenHasher() {
        newDigest();
    }

    public String hash(String rawToken) {
        byte[] hash = newDigest()
                .digest(Objects.requireNonNull(rawToken, "rawToken")
                        .getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 Hash를 사용할 수 없습니다.", exception);
        }
    }
}
