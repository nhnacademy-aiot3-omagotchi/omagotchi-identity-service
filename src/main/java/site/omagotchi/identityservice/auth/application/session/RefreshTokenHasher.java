package site.omagotchi.identityservice.auth.application.session;

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
        // Bean 생성 시점의 SHA-256 지원 여부 검증
        newDigest();
    }

    public String hash(String rawRefreshToken) {
        // 충분한 엔트로피의 난수 Token에 대한 단방향 SHA-256 Hash 적용
        byte[] hash = newDigest()
                .digest(Objects.requireNonNull(rawRefreshToken, "rawRefreshToken")
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
