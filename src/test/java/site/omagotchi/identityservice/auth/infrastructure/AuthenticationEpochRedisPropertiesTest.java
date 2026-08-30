package site.omagotchi.identityservice.auth.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class AuthenticationEpochRedisPropertiesTest {

    private static final String PREFIX = "auth.authentication-epoch.redis.";
    private static final String PASSWORD = "test-only-authentication-epoch-password";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    @DisplayName("잘못된 Authentication Epoch Redis 설정 시 기동 거부")
    void rejectsInvalidSetting(
            String ignoredDescription,
            String invalidProperty,
            String expectedMessage
    ) {
        // Given
        ApplicationContextRunner invalidContext = contextRunner
                .withPropertyValues(validSettings())
                .withPropertyValues(invalidProperty);

        // When
        invalidContext.run(context -> {
            // Then
            then(context.getStartupFailure())
                    .isNotNull()
                    .hasStackTraceContaining(expectedMessage);
        });
    }

    @Test
    @DisplayName("Authentication Epoch Redis 설정 바인딩")
    void bindsValidSettings() {
        // Given
        ApplicationContextRunner validContext = contextRunner
                .withPropertyValues(validSettings());

        // When
        validContext.run(context -> {
            // Then
            AuthenticationEpochRedisProperties properties =
                    context.getBean(AuthenticationEpochRedisProperties.class);
            thenSoftly(softly -> {
                softly.then(context.getStartupFailure()).isNull();
                softly.then(properties.host()).isEqualTo("localhost");
                softly.then(properties.port()).isEqualTo(6379);
                softly.then(properties.database()).isEqualTo(0);
                softly.then(properties.username()).isEqualTo("epoch-user");
                softly.then(properties.password()).isEqualTo(PASSWORD);
                softly.then(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
                softly.then(properties.commandTimeout()).isEqualTo(Duration.ofSeconds(2));
                softly.then(properties.sslEnabled()).isFalse();
            });
        });
    }

    @Test
    @DisplayName("바인딩된 Authentication Epoch Redis 비밀번호 마스킹")
    void redactsBoundPassword() {
        // Given
        ApplicationContextRunner validContext = contextRunner
                .withPropertyValues(validSettings());

        // When
        validContext.run(context -> {
            // Then
            AuthenticationEpochRedisProperties properties =
                    context.getBean(AuthenticationEpochRedisProperties.class);
            then(properties.toString())
                    .contains("password=[REDACTED]")
                    .doesNotContain(PASSWORD);
        });
    }

    private static String[] validSettings() {
        return new String[]{
                PREFIX + "host=localhost",
                PREFIX + "port=6379",
                PREFIX + "database=0",
                PREFIX + "username=epoch-user",
                PREFIX + "password=" + PASSWORD,
                PREFIX + "connect-timeout=1s",
                PREFIX + "command-timeout=2s",
                PREFIX + "ssl-enabled=false"
        };
    }

    private static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of(
                        "host 공백",
                        PREFIX + "host=",
                        "auth.authentication-epoch.redis.host는 비어 있을 수 없습니다."
                ),
                Arguments.of(
                        "port 최솟값 미만",
                        PREFIX + "port=0",
                        "auth.authentication-epoch.redis.port는 1 이상이어야 합니다."
                ),
                Arguments.of(
                        "port 최댓값 초과",
                        PREFIX + "port=65536",
                        "auth.authentication-epoch.redis.port는 65535 이하여야 합니다."
                ),
                Arguments.of(
                        "database 음수",
                        PREFIX + "database=-1",
                        "auth.authentication-epoch.redis.database는 0 이상이어야 합니다."
                ),
                Arguments.of(
                        "username 공백",
                        PREFIX + "username=",
                        "auth.authentication-epoch.redis.username은 비어 있을 수 없습니다."
                ),
                Arguments.of(
                        "password 공백",
                        PREFIX + "password=",
                        "auth.authentication-epoch.redis.password는 비어 있을 수 없습니다."
                ),
                Arguments.of(
                        "connect timeout 0초",
                        PREFIX + "connect-timeout=0s",
                        "auth.authentication-epoch.redis.connect-timeout은 0보다 커야 합니다."
                ),
                Arguments.of(
                        "command timeout 0초",
                        PREFIX + "command-timeout=0s",
                        "auth.authentication-epoch.redis.command-timeout은 0보다 커야 합니다."
                )
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthenticationEpochRedisProperties.class)
    static class PropertiesConfig {
    }
}
