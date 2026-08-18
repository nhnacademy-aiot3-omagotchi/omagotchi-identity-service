package site.omagotchi.identityservice.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import static org.assertj.core.api.BDDAssertions.then;

class JwtAuthorityConfigTest {

    private static final String USER_ID = "019d2a48-80c0-4d6a-9a15-0b16d2dd74f1";

    @Test
    @DisplayName("role claim의 ROLE_ 권한 변환")
    void convertsRoleClaim() {
        // Given
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(USER_ID)
                .claim("role", "USER")
                .build();
        JwtAuthenticationConverter converter = new JwtAuthorityConfig()
                .jwtAuthenticationConverter();

        // When
        AbstractAuthenticationToken authentication = converter.convert(jwt);

        // Then
        then(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER");
    }
}
