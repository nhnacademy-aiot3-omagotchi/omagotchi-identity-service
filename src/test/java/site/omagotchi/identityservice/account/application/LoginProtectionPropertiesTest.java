package site.omagotchi.identityservice.account.application;

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

class LoginProtectionPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("로그인 잠금 설정 누락 시 기동 거부")
    void rejectsMissingSettings() {
        contextRunner.run(context -> then(context.getStartupFailure())
                .isNotNull()
                .hasStackTraceContaining(
                        "auth.login-protection.maximum-failed-attempts는 1 이상이어야 합니다."
                )
                .hasStackTraceContaining(
                        "auth.login-protection.lock-duration은 필수입니다."
                ));
    }

    @Test
    @DisplayName("잘못된 로그인 잠금 기간 형식 입력 시 기동 거부")
    void rejectsMalformedLockDuration() {
        contextRunner
                .withPropertyValues(
                        "auth.login-protection.maximum-failed-attempts=5",
                        "auth.login-protection.lock-duration=ten-minutes"
                )
                .run(context -> then(context.getStartupFailure())
                        .isNotNull()
                        .hasStackTraceContaining("auth.login-protection.lock-duration"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    @DisplayName("지원하지 않는 로그인 잠금 설정 거부")
    void rejectsInvalidSetting(
            String ignoredDescription,
            String maximumFailedAttempts,
            String lockDuration,
            String expectedMessage
    ) {
        contextRunner
                .withPropertyValues(
                        "auth.login-protection.maximum-failed-attempts="
                                + maximumFailedAttempts,
                        "auth.login-protection.lock-duration=" + lockDuration
                )
                .run(context -> then(context.getStartupFailure())
                        .isNotNull()
                        .hasStackTraceContaining(expectedMessage));
    }

    @Test
    @DisplayName("명시한 로그인 잠금 설정 바인딩")
    void bindsExplicitSettings() {
        contextRunner
                .withPropertyValues(
                        "auth.login-protection.maximum-failed-attempts=5",
                        "auth.login-protection.lock-duration=PT10M"
                )
                .run(context -> {
                    LoginProtectionProperties properties =
                            context.getBean(LoginProtectionProperties.class);
                    thenSoftly(softly -> {
                        softly.then(context.getStartupFailure()).isNull();
                        softly.then(properties.maximumFailedAttempts()).isEqualTo(5);
                        softly.then(properties.lockDuration()).isEqualTo(Duration.ofMinutes(10));
                    });
                });
    }

    private static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of(
                        "실패 횟수 0",
                        "0",
                        "PT10M",
                        "auth.login-protection.maximum-failed-attempts는 1 이상이어야 합니다."
                ),
                Arguments.of(
                        "SMALLINT 범위 초과",
                        "32768",
                        "PT10M",
                        "auth.login-protection.maximum-failed-attempts는 32767 이하여야 합니다."
                ),
                Arguments.of(
                        "잠금 기간 0초",
                        "5",
                        "PT0S",
                        "auth.login-protection.lock-duration은 1초 이상이어야 합니다."
                )
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoginProtectionProperties.class)
    static class PropertiesConfig {
    }
}
