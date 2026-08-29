package site.omagotchi.identityservice.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.auth.application.port.AccessTokenIssuer;
import site.omagotchi.identityservice.auth.application.result.IssuedAccessToken;
import site.omagotchi.identityservice.global.security.jwt.JwtProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    @Override
    public IssuedAccessToken issue(
            UUID accountId,
            String globalRole,
            UUID authenticationEpoch
    ) {
        // JWT NumericDate와 API 응답의 정밀도 일치를 위한 초 단위 발급 시각
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        // sub는 계정 식별자, jti는 개별 Access Token 식별자
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(accountId.toString())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("role", globalRole)
                .claim("auth_epoch", authenticationEpoch.toString())
                .build();
        JwsHeader header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .type("JWT")
                .build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
        return new IssuedAccessToken(
                value,
                expiresAt
        );
    }
}
