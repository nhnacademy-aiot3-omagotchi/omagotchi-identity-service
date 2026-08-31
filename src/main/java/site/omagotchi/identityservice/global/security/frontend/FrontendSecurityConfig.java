package site.omagotchi.identityservice.global.security.frontend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import site.omagotchi.identityservice.global.security.basic.ServiceCredentialAuthenticationProviderFactory;
import site.omagotchi.identityservice.global.security.error.SecurityErrorResponseHandler;

// 가입·Token 수명주기 API의 Frontend 프로세스 전용 HTTP Basic 인증 경계
@Configuration
public class FrontendSecurityConfig {

    private static final String FRONTEND_ROLE = "FRONTEND";
    private static final String FRONTEND_REALM = "omagotchi-identity-frontend";

    // Frontend 인증 API를 기본 Bearer JWT 경계보다 먼저 선택하는 전용 Filter Chain
    @Bean
    @Order(1)
    SecurityFilterChain frontendSecurityFilterChain(
            HttpSecurity http,
            SecurityErrorResponseHandler errorHandler,
            ServiceCredentialAuthenticationProviderFactory providerFactory,
            FrontendCredentialProperties properties
    ) {
        http
                // 본 Filter Chain에 포함할 Frontend 전용 인증 API 경로
                .securityMatcher("/api/v1/auth/**", "/api/v2/auth/**")
                // Browser Cookie 인증을 사용하지 않는 내부 HTTP API
                .csrf(AbstractHttpConfigurer::disable)
                // Browser의 Identity 직접 호출 금지
                .cors(AbstractHttpConfigurer::disable)
                // JSON API 전용 인증 경계
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        // 요청별 HTTP Basic 인증과 서버 Session 미사용
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Frontend 프로세스 Credential 전용 Provider의 명시적 등록
                .authenticationProvider(providerFactory.create(
                        properties.username(),
                        properties.password(),
                        FRONTEND_ROLE
                ))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasRole(FRONTEND_ROLE)
                )
                // HTTP Basic 실패의 challenge와 공통 JSON 응답 지정
                .httpBasic(httpBasic -> httpBasic
                        .authenticationEntryPoint(
                                errorHandler.basicAuthenticationEntryPoint(FRONTEND_REALM)
                        )
                )
                // 인증 방식 외의 Filter Chain 접근 거부까지 같은 JSON 계약 적용
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                errorHandler.basicAuthenticationEntryPoint(FRONTEND_REALM)
                        )
                        .accessDeniedHandler(errorHandler.basicAccessDeniedHandler())
                );

        return http.build();
    }
}
