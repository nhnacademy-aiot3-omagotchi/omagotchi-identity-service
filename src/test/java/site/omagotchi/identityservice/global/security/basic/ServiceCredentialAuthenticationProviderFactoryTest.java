package site.omagotchi.identityservice.global.security.basic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.BDDAssertions.then;

class ServiceCredentialAuthenticationProviderFactoryTest {

    private final ServiceCredentialAuthenticationProviderFactory factory =
            new ServiceCredentialAuthenticationProviderFactory(new BCryptPasswordEncoder());

    @Test
    @DisplayName("서비스 Credential 인증과 역할 부여")
    void authenticatesCredentialAndAssignsRole() {
        // Given
        AuthenticationProvider provider = factory.create(
                "learning-service",
                "test-only-learning-credential-password",
                "LEARNING"
        );
        Authentication request = UsernamePasswordAuthenticationToken.unauthenticated(
                "learning-service",
                "test-only-learning-credential-password"
        );

        // When
        Authentication result = provider.authenticate(request);

        // Then
        then(result).isNotNull();
        then(result.isAuthenticated()).isTrue();
        then(result.getAuthorities())
                .extracting("authority")
                .contains("ROLE_LEARNING");
    }

    @Test
    @DisplayName("잘못된 서비스 Credential 거부")
    void rejectsInvalidCredential() {
        // Given
        AuthenticationProvider provider = factory.create(
                "learning-service",
                "test-only-learning-credential-password",
                "LEARNING"
        );
        Authentication request = UsernamePasswordAuthenticationToken.unauthenticated(
                "learning-service",
                "wrong-password"
        );

        // When
        // Then
        assertThatThrownBy(() -> provider.authenticate(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
