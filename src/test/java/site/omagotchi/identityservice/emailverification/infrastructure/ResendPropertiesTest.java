package site.omagotchi.identityservice.emailverification.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.BDDAssertions.then;

class ResendPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("Resend 시간 제한 누락 시 기동 거부")
    void rejectsMissingTimeouts() {
        // Given
        contextRunner.withPropertyValues(
                        "email.resend.api-key=test-api-key",
                        "email.resend.from-email=no-reply@omagotchi.test"
                )
                // When
                .run(context -> then(context.getStartupFailure())
                        // Then
                        .isNotNull()
                        .hasStackTraceContaining("email.resend.connect-timeout은 필수입니다.")
                        .hasStackTraceContaining("email.resend.read-timeout은 필수입니다."));
    }

    @Test
    @DisplayName("0 이하의 Resend 시간 제한 거부")
    void rejectsNonPositiveTimeouts() {
        // Given
        contextRunner.withPropertyValues(
                        "email.resend.api-key=test-api-key",
                        "email.resend.from-email=no-reply@omagotchi.test",
                        "email.resend.connect-timeout=PT0S",
                        "email.resend.read-timeout=PT0S"
                )
                // When
                .run(context -> then(context.getStartupFailure())
                        // Then
                        .isNotNull()
                        .hasStackTraceContaining("connect-timeout은 1ms 이상이어야 합니다.")
                        .hasStackTraceContaining("read-timeout은 1ms 이상이어야 합니다."));
    }

    @Test
    @DisplayName("명시한 Resend 시간 제한 바인딩")
    void bindsExplicitTimeouts() {
        // Given
        String apiKey = "test-api-key";
        contextRunner.withPropertyValues(
                        "email.resend.api-key=" + apiKey,
                        "email.resend.from-email=no-reply@omagotchi.test",
                        "email.resend.connect-timeout=PT2S",
                        "email.resend.read-timeout=PT5S"
                )
                // When
                .run(context -> {
                    // Then
                    ResendProperties properties = context.getBean(ResendProperties.class);
                    then(context.getStartupFailure()).isNull();
                    then(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    then(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
                    then(properties.toString())
                            .contains("[REDACTED]")
                            .doesNotContain(apiKey);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ResendProperties.class)
    static class PropertiesConfig {
    }
}
