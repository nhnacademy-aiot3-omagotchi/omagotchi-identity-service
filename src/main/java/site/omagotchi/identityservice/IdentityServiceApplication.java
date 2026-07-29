package site.omagotchi.identityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import site.omagotchi.identityservice.auth.application.RefreshTokenProperties;
import site.omagotchi.identityservice.auth.presentation.RefreshTokenWebProperties;
import site.omagotchi.identityservice.global.security.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        RefreshTokenProperties.class,
        RefreshTokenWebProperties.class
})
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
