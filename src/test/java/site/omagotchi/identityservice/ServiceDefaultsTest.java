package site.omagotchi.identityservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import site.omagotchi.identityservice.account.application.LoginProtectionProperties;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationProperties;
import site.omagotchi.identityservice.emailverification.infrastructure.ResendProperties;

import java.time.Duration;

import static org.assertj.core.api.BDDAssertions.then;

class ServiceDefaultsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "spring.config.location=classpath:/application.yaml",
                    "EMAIL_VERIFICATION_HMAC_SECRET=test-only-hmac-secret-with-at-least-32-characters",
                    "RESEND_API_KEY=test-only-api-key",
                    "RESEND_FROM_EMAIL=test@example.invalid"
            )
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("인증정보만 주입한 상태의 로그인·이메일 정책 기본값 적용")
    void bindsDefaultsWithoutPolicyEnvironmentVariables() {
        // Given: 별도 정책 환경변수 없는 서비스 기본 설정
        // When
        contextRunner.run(context -> {
            // Then: 필수 정책 바인딩과 이메일 유효시간 안의 외부 호출 상한
            then(context).hasNotFailed();
            LoginProtectionProperties login = context.getBean(LoginProtectionProperties.class);
            EmailVerificationProperties email = context.getBean(EmailVerificationProperties.class);
            ResendProperties resend = context.getBean(ResendProperties.class);
            then(login.maximumFailedAttempts()).isPositive();
            then(login.lockDuration()).isPositive();
            then(resend.connectTimeout()).isLessThanOrEqualTo(resend.readTimeout());
            then(resend.readTimeout()).isLessThan(email.codeTtl());
            then(email.cooldown()).isLessThan(email.codeTtl());
        });
    }

    @Test
    @DisplayName("기존 로그인 정책 환경변수의 기본값 덮어쓰기 유지")
    void preservesLegacyEnvironmentOverrides() {
        // Given: Infra 전환 전의 기존 키
        // When
        contextRunner.withPropertyValues("LOGIN_LOCK_DURATION=PT20M")
                .run(context -> {
                    // Then
                    then(context).hasNotFailed();
                    then(context.getBean(LoginProtectionProperties.class).lockDuration())
                            .isEqualTo(Duration.ofMinutes(20));
                });
    }

    @Test
    @DisplayName("정책 기본값이 있어도 HMAC 비밀값 누락 시 기동 거부")
    void rejectsMissingSecret() {
        // Given: 비밀값의 빈 입력
        // When
        contextRunner.withPropertyValues("EMAIL_VERIFICATION_HMAC_SECRET=")
                .run(context -> {
                    // Then
                    then(context).hasFailed();
                    then(context.getStartupFailure()).hasStackTraceContaining("hmac-secret은 필수입니다.");
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties({LoginProtectionProperties.class, EmailVerificationProperties.class,
            ResendProperties.class})
    static class PropertiesConfig {
    }
}
