package site.omagotchi.identityservice.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import site.omagotchi.identityservice.account.domain.GlobalRole;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Configuration
public class JwtConfig {

    private static final int MIN_RSA_KEY_SIZE = 2048;

    @Bean
    JwtEncoder jwtEncoder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        // 잘못된 key로 인한 인증 서버 실행 방지용 시작 시점 검증
        validateKeyPair(publicKey, privateKey);
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build();
    }

    @Bean
    JwtDecoder jwtDecoder(RSAPublicKey publicKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud",
                audience -> audience.contains(properties.audience())
        );
        OAuth2TokenValidator<Jwt> subjectValidator = new JwtClaimValidator<>(
                "sub",
                JwtConfig::isValidSubject
        );
        OAuth2TokenValidator<Jwt> roleValidator = new JwtClaimValidator<>(
                "role",
                GlobalRole::isSupported
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator,
                subjectValidator,
                roleValidator
        ));
        return decoder;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    // RSA key 크기와 공개·개인 key 일치 여부의 시작 시점 검증
    private static void validateKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        if (publicKey.getModulus().bitLength() < MIN_RSA_KEY_SIZE) {
            throw new IllegalStateException("RSA key는 2048 bit 이상이어야 합니다.");
        }
        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalStateException("JWT public key와 private key가 서로 일치하지 않습니다.");
        }
    }

    private static boolean isValidSubject(String subject) {
        if (subject == null) {
            return false;
        }

        try {
            UUID accountId = UUID.fromString(subject);
            return accountId.toString().equals(subject);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
