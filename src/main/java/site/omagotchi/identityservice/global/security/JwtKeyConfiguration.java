package site.omagotchi.identityservice.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * PEM 파일 -> Java RSA Key 객체
 * 흐름:
 * - application.yaml의 key 파일 위치
 * -> JwtProperties의 Resource
 * -> JwtKeyConfiguration이 PEM 파일 읽기
 * -> RSAPublicKey/RSAPrivateKey Bean 생성
 * -> JwtConfiguration이 JwtEncoder/JwtDecoder 생성
 */
@Configuration
@Profile("!test")
public class JwtKeyConfiguration {

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
