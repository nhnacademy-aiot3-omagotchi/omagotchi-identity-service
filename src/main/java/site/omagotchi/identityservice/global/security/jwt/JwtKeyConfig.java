package site.omagotchi.identityservice.global.security.jwt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

// PEM key를 Bean으로 준비하지 못하면 애플리케이션 시작 중단
@Configuration
@Profile("!test")
public class JwtKeyConfig {

    @Bean
    RSAPublicKey jwtPublicKey(JwtProperties properties) {
        try (InputStream inputStream = properties.publicKeyLocation().getInputStream()) {
            return RsaKeyConverters.x509().convert(inputStream);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("JWT public key를 읽을 수 없습니다.", exception);
        }
    }

    @Bean
    RSAPrivateKey jwtPrivateKey(JwtProperties properties) {
        try (InputStream inputStream = properties.privateKeyLocation().getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(inputStream);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("JWT private key를 읽을 수 없습니다.", exception);
        }
    }
}
