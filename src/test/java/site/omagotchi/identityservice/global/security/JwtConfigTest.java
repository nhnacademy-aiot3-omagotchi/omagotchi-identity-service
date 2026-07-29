package site.omagotchi.identityservice.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class JwtConfigTest {

    private static final String USER_ID = "019d2a48-80c0-4d6a-9a15-0b16d2dd74f1";

    private final JwtConfig jwtConfig = new JwtConfig();

    @Test
    @DisplayName("2048 bit RSA key pair 허용")
    void acceptsMinimumSizeKeyPair() throws Exception {
        // Given
        KeyPair keyPair = generateKeyPair(2048);

        // When
        Throwable thrown = catchThrowable(() -> jwtConfig.jwtEncoder(
                (RSAPublicKey) keyPair.getPublic(),
                (RSAPrivateKey) keyPair.getPrivate()
        ));

        // Then
        then(thrown).isNull();
    }

    @Test
    @DisplayName("2048 bit 초과 RSA key pair 허용")
    void acceptsLargerKeyPair() throws Exception {
        // Given
        KeyPair keyPair = generateKeyPair(3072);

        // When
        Throwable thrown = catchThrowable(() -> jwtConfig.jwtEncoder(
                (RSAPublicKey) keyPair.getPublic(),
                (RSAPrivateKey) keyPair.getPrivate()
        ));

        // Then
        then(thrown).isNull();
    }

    @Test
    @DisplayName("불일치 RSA key pair 거부")
    void rejectsMismatchedKeyPair() throws Exception {
        // Given
        KeyPair first = generateKeyPair(2048);
        KeyPair second = generateKeyPair(2048);

        // When
        Throwable thrown = catchThrowable(() -> jwtConfig.jwtEncoder(
                (RSAPublicKey) first.getPublic(),
                (RSAPrivateKey) second.getPrivate()
        ));

        // Then
        then(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("2048 bit 미만 RSA key 거부")
    void rejectsWeakKeyPair() throws Exception {
        // Given
        KeyPair weak = generateKeyPair(1024);

        // When
        Throwable thrown = catchThrowable(() -> jwtConfig.jwtEncoder(
                (RSAPublicKey) weak.getPublic(),
                (RSAPrivateKey) weak.getPrivate()
        ));

        // Then
        then(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("지원하는 전역 역할 허용")
    void acceptsSupportedGlobalRoles() throws Exception {
        // Given
        KeyPair keyPair = generateKeyPair(2048);
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        JwtEncoder encoder = jwtConfig.jwtEncoder(
                publicKey,
                (RSAPrivateKey) keyPair.getPrivate()
        );
        JwtDecoder decoder = jwtConfig.jwtDecoder(publicKey, jwtProperties());
        List<String> tokens = List.of(
                issue(encoder, "https://identity.omagotchi.local", "omagotchi-api", USER_ID, "USER"),
                issue(encoder, "https://identity.omagotchi.local", "omagotchi-api", USER_ID, "SYSTEM_ADMIN")
        );

        // When
        List<String> roles = tokens.stream()
                .map(token -> decoder.decode(token).getClaimAsString("role"))
                .toList();

        // Then
        then(roles).containsExactly("USER", "SYSTEM_ADMIN");
    }

    @Test
    @DisplayName("JWT claim 계약 위반 거부")
    void rejectsInvalidClaims() throws Exception {
        // Given
        KeyPair keyPair = generateKeyPair(2048);
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        JwtEncoder encoder = jwtConfig.jwtEncoder(
                publicKey,
                (RSAPrivateKey) keyPair.getPrivate()
        );
        JwtDecoder decoder = jwtConfig.jwtDecoder(publicKey, jwtProperties());
        List<String> invalidTokens = List.of(
                issue(encoder, "https://other-issuer.example", "omagotchi-api", USER_ID, "USER"),
                issue(encoder, "https://identity.omagotchi.local", "other-api", USER_ID, "USER"),
                issue(encoder, "https://identity.omagotchi.local", "omagotchi-api", "not-a-uuid", "USER"),
                issue(encoder, "https://identity.omagotchi.local", "omagotchi-api", USER_ID, "ADMIN"),
                issue(encoder, "https://identity.omagotchi.local", "omagotchi-api", USER_ID, "MANAGER")
        );

        // When
        List<Throwable> failures = invalidTokens.stream()
                .map(token -> catchThrowable(() -> decoder.decode(token)))
                .toList();

        // Then
        thenSoftly(softly -> failures.forEach(failure ->
                softly.then(failure).isInstanceOf(JwtValidationException.class)
        ));
    }

    @Test
    @DisplayName("변조/만료 JWT 거부")
    void rejectsTamperedAndExpiredTokens() throws Exception {
        // Given
        KeyPair keyPair = generateKeyPair(2048);
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        JwtEncoder encoder = jwtConfig.jwtEncoder(
                publicKey,
                (RSAPrivateKey) keyPair.getPrivate()
        );
        JwtDecoder decoder = jwtConfig.jwtDecoder(publicKey, jwtProperties());
        String validToken = issue(
                encoder,
                "https://identity.omagotchi.local",
                "omagotchi-api",
                USER_ID,
                "USER"
        );
        Instant expiresAt = Instant.now().minusSeconds(60);
        List<String> invalidTokens = List.of(
                tamperSignature(validToken),
                issue(
                        encoder,
                        "https://identity.omagotchi.local",
                        "omagotchi-api",
                        USER_ID,
                        "USER",
                        expiresAt.minusSeconds(60),
                        expiresAt
                )
        );

        // When
        List<Throwable> failures = invalidTokens.stream()
                .map(token -> catchThrowable(() -> decoder.decode(token)))
                .toList();

        // Then
        thenSoftly(softly -> failures.forEach(failure ->
                softly.then(failure).isInstanceOf(JwtException.class)
        ));
    }

    private String issue(
            JwtEncoder encoder,
            String issuer,
            String audience,
            String subject,
            String role
    ) {
        Instant now = Instant.now();
        return issue(
                encoder,
                issuer,
                audience,
                subject,
                role,
                now,
                now.plusSeconds(60)
        );
    }

    private String issue(
            JwtEncoder encoder,
            String issuer,
            String audience,
            String subject,
            String role,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", role)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("JWT")
                .build();

        return encoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        String signature = parts[2];
        char replacement = signature.charAt(0) == 'A' ? 'B' : 'A';

        return parts[0] + "." + parts[1] + "." + replacement + signature.substring(1);
    }

    private JwtProperties jwtProperties() {
        ByteArrayResource unusedKeyLocation = new ByteArrayResource(new byte[0]);
        return new JwtProperties(
                "https://identity.omagotchi.local",
                "omagotchi-api",
                Duration.ofMinutes(15),
                unusedKeyLocation,
                unusedKeyLocation
        );
    }

    private KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize);
        return generator.generateKeyPair();
    }
}
