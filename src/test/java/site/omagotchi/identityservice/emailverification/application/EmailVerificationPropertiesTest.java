package site.omagotchi.identityservice.emailverification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.BDDAssertions.then;

class EmailVerificationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("이메일 인증 필수 설정 누락 시 기동 거부")
    void rejectsMissingSettings() {
        // Given
        // 필수 설정을 주입하지 않는다.

        // When
        contextRunner.run(context -> then(context.getStartupFailure())
                // Then
                .isNotNull()
                .hasStackTraceContaining("auth.email-verification.code-ttl은 필수입니다.")
                .hasStackTraceContaining("auth.email-verification.cooldown은 필수입니다.")
                .hasStackTraceContaining("auth.email-verification.hmac-secret은 필수입니다."));
    }

    @Test
    @DisplayName("짧은 HMAC 비밀값과 0초 정책 거부")
    void rejectsUnsafeSettings() {
        // Given
        contextRunner.withPropertyValues(
                        "auth.email-verification.code-ttl=PT0S",
                        "auth.email-verification.cooldown=PT0S",
                        "auth.email-verification.maximum-failed-attempts=0",
                        "auth.email-verification.hmac-secret=short"
                )
                // When
                .run(context -> then(context.getStartupFailure())
                        // Then
                        .isNotNull()
                        .hasStackTraceContaining("code-ttl은 1초 이상이어야 합니다.")
                        .hasStackTraceContaining("cooldown은 1초 이상이어야 합니다.")
                        .hasStackTraceContaining("maximum-failed-attempts는 1 이상이어야 합니다.")
                        .hasStackTraceContaining("hmac-secret은 32자 이상이어야 합니다."));
    }

    @Test
    @DisplayName("명시한 이메일 인증 정책 바인딩")
    void bindsExplicitSettings() {
        // Given
        contextRunner.withPropertyValues(
                        "auth.email-verification.code-ttl=PT5M",
                        "auth.email-verification.cooldown=PT1M",
                        "auth.email-verification.maximum-failed-attempts=5",
                        "auth.email-verification.hmac-secret=test-hmac-secret-with-at-least-32-characters"
                )
                // When
                .run(context -> {
                    // Then
                    EmailVerificationProperties properties =
                            context.getBean(EmailVerificationProperties.class);
                    then(context.getStartupFailure()).isNull();
                    then(properties.codeTtl()).isEqualTo(Duration.ofMinutes(5));
                    then(properties.cooldown()).isEqualTo(Duration.ofMinutes(1));
                    then(properties.maximumFailedAttempts()).isEqualTo(5);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailVerificationProperties.class)
    static class PropertiesConfig {
    }
}
