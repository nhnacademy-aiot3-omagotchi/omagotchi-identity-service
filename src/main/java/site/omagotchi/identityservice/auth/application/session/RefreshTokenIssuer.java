package site.omagotchi.identityservice.auth.application.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.auth.application.result.IssuedRefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshToken;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenIssuer {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    // 새 로그인별 독립 family와 고정 만료 시각 생성
    public IssuedRefreshToken issueNewFamily(UUID accountId, Instant issuedAt) {
        return issue(
                accountId,
                UUID.randomUUID(),
                issuedAt.plus(properties.ttl()),
                issuedAt
        );
    }

    // 갱신 시 기존 family와 최초 로그인 기준 만료 시각 유지
    public IssuedRefreshToken issue(
            UUID accountId,
            UUID familyId,
            Instant expiresAt,
            Instant issuedAt
    ) {
        String rawRefreshToken = generate();
        RefreshToken refreshToken = RefreshToken.issue(
                accountId,
                familyId,
                refreshTokenHasher.hash(rawRefreshToken),
                expiresAt,
                issuedAt
        );

        return new IssuedRefreshToken(rawRefreshToken, refreshToken);
    }

    private String generate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
