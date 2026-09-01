package site.omagotchi.identityservice.global.security.frontend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Stream;

import static org.assertj.core.api.BDDAssertions.then;

class FrontendCredentialPropertiesTest {

    private static final String VALID_PASSWORD =
            "test-only-frontend-credential-password";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    @DisplayName("잘못된 Frontend Credential 설정 시 기동 거부")
    void rejectsInvalidSetting(
            String ignoredDescription,
            String[] configuredProperties,
            String expectedMessage
    ) {
        // Given
        // MethodSource에서 잘못된 Credential 설정을 제공한다.

        // When
        contextRunner
                .withPropertyValues(configuredProperties)
                .run(context -> {
                    // Then
                    then(context.getStartupFailure())
                            .isNotNull()
                            .hasStackTraceContaining(expectedMessage);
                });
    }

    @Test
    @DisplayName("잘못된 Frontend 비밀번호를 기동 실패 메시지에 노출하지 않음")
    void doesNotExposeInvalidPassword() {
        // Given
        String invalidPassword = "too-short";

        // When
        contextRunner
                .withPropertyValues(
                        "auth.frontend.username=frontend",
                        "auth.frontend.password=" + invalidPassword
                )
                .run(context -> {
                    // Then
                    Throwable failure = context.getStartupFailure();
                    then(failure).isNotNull();
                    then(stackTrace(failure)).doesNotContain(invalidPassword);
                });
    }

    @Test
    @DisplayName("바인딩된 Frontend 비밀번호 마스킹")
    void redactsBoundPassword() {
        // Given
        String passwordProperty = "auth.frontend.password=" + VALID_PASSWORD;

        // When
        contextRunner
                .withPropertyValues(
                        "auth.frontend.username=frontend",
                        passwordProperty
                )
                .run(context -> {
                    // Then
                    FrontendCredentialProperties properties =
                            context.getBean(FrontendCredentialProperties.class);
                    then(properties.toString())
                            .contains("[REDACTED]")
                            .doesNotContain(VALID_PASSWORD);
                });
    }

    @Test
    @DisplayName("URL-safe ASCII 32자 Frontend 비밀번호 허용")
    void acceptsMinimumPasswordLength() {
        // Given
        String minimumLengthPassword = "a".repeat(32);

        // When
        contextRunner
                .withPropertyValues(
                        "auth.frontend.username=frontend",
                        "auth.frontend.password=" + minimumLengthPassword
                )
                .run(context -> {
                    // Then
                    then(context.getStartupFailure()).isNull();
                    then(context).hasSingleBean(FrontendCredentialProperties.class);
                });
    }

    @Test
    @DisplayName("URL-safe ASCII 31자 Frontend 비밀번호 거부")
    void rejectsPasswordBelowMinimumLength() {
        // Given
        String belowMinimumPassword = "a".repeat(31);

        // When
        contextRunner
                .withPropertyValues(
                        "auth.frontend.username=frontend",
                        "auth.frontend.password=" + belowMinimumPassword
                )
                .run(context -> {
                    // Then
                    then(context.getStartupFailure())
                            .isNotNull()
                            .hasStackTraceContaining(
                                    "auth.frontend.password는 32자 이상 72자 이하여야 합니다."
                            );
                });
    }

    @Test
    @DisplayName("URL-safe ASCII 72자 Frontend 비밀번호 허용")
    void acceptsMaximumPasswordLength() {
        // Given
        String maximumLengthPassword = "a".repeat(72);

        // When
        contextRunner
                .withPropertyValues(
                        "auth.frontend.username=frontend",
                        "auth.frontend.password=" + maximumLengthPassword
                )
                .run(context -> {
                    // Then
                    then(context.getStartupFailure()).isNull();
                    then(context).hasSingleBean(FrontendCredentialProperties.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FrontendCredentialProperties.class)
    static class PropertiesConfig {
    }

    private String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    private static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of(
                        "username 누락",
                        new String[]{"auth.frontend.password=" + VALID_PASSWORD},
                        "auth.frontend.username은 비어 있을 수 없습니다."
                ),
                Arguments.of(
                        "password 누락",
                        new String[]{"auth.frontend.username=frontend"},
                        "auth.frontend.password는 비어 있을 수 없습니다."
                ),
                Arguments.of(
                        "Unicode password",
                        new String[]{
                                "auth.frontend.username=frontend",
                                "auth.frontend.password=" + "가".repeat(32)
                        },
                        "auth.frontend.password는 영문자·숫자·'-'·'_'만 사용할 수 있습니다."
                ),
                Arguments.of(
                        "Base64URL에 포함되지 않는 ASCII password",
                        new String[]{
                                "auth.frontend.username=frontend",
                                "auth.frontend.password=" + "a".repeat(31) + "+"
                        },
                        "auth.frontend.password는 영문자·숫자·'-'·'_'만 사용할 수 있습니다."
                ),
                Arguments.of(
                        "Basic username 구분자 포함",
                        new String[]{
                                "auth.frontend.username=front:end",
                                "auth.frontend.password=" + VALID_PASSWORD
                        },
                        "auth.frontend.username에는 ':'를 사용할 수 없습니다."
                ),
                Arguments.of(
                        "password 72자 길이 초과",
                        new String[]{
                                "auth.frontend.username=frontend",
                                "auth.frontend.password=" + "a".repeat(73)
                        },
                        "auth.frontend.password는 32자 이상 72자 이하여야 합니다."
                )
        );
    }
}
