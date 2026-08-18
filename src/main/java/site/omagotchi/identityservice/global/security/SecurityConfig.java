package site.omagotchi.identityservice.global.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

// Frontend 전용 인증 API를 제외한 Identity 보호 API의 Bearer JWT 경계
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityErrorResponseHandler errorHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) {
        http
                // Cookie 인증을 사용하지 않는 Bearer Token API
                .csrf(AbstractHttpConfigurer::disable)
                // Browser의 Domain Service 직접 호출 금지
                .cors(AbstractHttpConfigurer::disable)
                // Bearer JWT 전용 인증 경계
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        // 요청별 Bearer JWT 인증과 서버 Session 미사용
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> authorize
                        // 원본 오류 상태 유지를 위한 ERROR 재디스패치 허용
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // Bearer Token 해석·인증 실패에 대한 RFC 6750 Header와 공통 JSON 응답
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                )
                // 인증 방식 외의 Filter Chain 접근 거부까지 같은 JSON 계약 적용
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                );

        return http.build();
    }
}
