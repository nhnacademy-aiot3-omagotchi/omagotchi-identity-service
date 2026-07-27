package site.omagotchi.identityservice.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.auth.domain.RefreshToken;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenIssuer {

    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenProperties properties;

    // 새 로그인마다 독립적인 family와 고정 만료 시각 생성
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
        String value = refreshTokenGenerator.generate();
        RefreshToken refreshToken = RefreshToken.issue(
                accountId,
                familyId,
                refreshTokenHasher.hash(value),
                expiresAt,
                issuedAt
        );

        return new IssuedRefreshToken(value, refreshToken);
    }
}
