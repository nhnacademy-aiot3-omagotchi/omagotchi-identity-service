package site.omagotchi.identityservice.email.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.backoff.BackOffExecution;
import site.omagotchi.identityservice.email.application.port.EmailDeliveryException;

import java.time.Duration;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class EmailConfigTest {

    @Test
    @DisplayName("인증 메일 재시도를 최대 두 번의 지수 Backoff와 Jitter로 제한")
    void configuresBoundedVerificationMailRetry() {
        RetryTemplate retryTemplate = new EmailConfig().verificationMailRetryTemplate();
        RetryPolicy retryPolicy = retryTemplate.getRetryPolicy();
        BackOffExecution backOff = retryPolicy.getBackOff().start();

        long firstDelay = backOff.nextBackOff();
        long secondDelay = backOff.nextBackOff();
        long exhausted = backOff.nextBackOff();

        thenSoftly(softly -> {
            softly.then(retryPolicy.getTimeout()).isEqualTo(Duration.ofSeconds(5));
            softly.then(retryPolicy.shouldRetry(
                    new EmailDeliveryException(503, "temporary_error", true)
            )).isTrue();
            softly.then(retryPolicy.shouldRetry(
                    new EmailDeliveryException(400, "validation_error", false)
            )).isFalse();
            softly.then(firstDelay).isBetween(1_000L, 1_250L);
            softly.then(secondDelay).isBetween(1_500L, 2_000L);
            softly.then(exhausted).isEqualTo(BackOffExecution.STOP);
        });
    }
}
