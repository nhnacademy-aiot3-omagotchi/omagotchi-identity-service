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

    public IssuedRefreshToken issueNewFamily(UUID accountId, Instant issuedAt) {
        return issue(
                accountId,
                UUID.randomUUID(),
                issuedAt.plus(properties.ttl()),
                issuedAt
        );
    }

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
