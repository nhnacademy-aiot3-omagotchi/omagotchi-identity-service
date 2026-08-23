package site.omagotchi.identityservice.global.security.learning;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import site.omagotchi.identityservice.global.security.basic.ServiceCredentialAuthenticationProviderFactory;
import site.omagotchi.identityservice.global.security.error.SecurityErrorResponseHandler;

// Learning 전용 계정 조회 API의 서비스 인증 경계
@Configuration
public class LearningSecurityConfig {

    private static final String LEARNING_ROLE = "LEARNING";
    private static final String LEARNING_REALM = "omagotchi-identity-learning";

    @Bean
    @Order(2)
    SecurityFilterChain learningSecurityFilterChain(
            HttpSecurity http,
            SecurityErrorResponseHandler errorHandler,
            ServiceCredentialAuthenticationProviderFactory providerFactory,
            LearningCredentialProperties properties
    ) {
        http
                .securityMatcher("/api/v1/internal/accounts/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(providerFactory.create(
                        properties.username(),
                        properties.password(),
                        LEARNING_ROLE
                ))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasRole(LEARNING_ROLE)
                )
                .httpBasic(httpBasic -> httpBasic
                        .authenticationEntryPoint(
                                errorHandler.basicAuthenticationEntryPoint(LEARNING_REALM)
                        )
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                errorHandler.basicAuthenticationEntryPoint(LEARNING_REALM)
                        )
                        .accessDeniedHandler(errorHandler.basicAccessDeniedHandler())
                );

        return http.build();
    }
}
