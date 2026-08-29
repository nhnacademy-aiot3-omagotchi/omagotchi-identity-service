package site.omagotchi.identityservice.email.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.backoff.FixedBackOff;
import site.omagotchi.identityservice.email.application.port.EmailDeliveryException;
import site.omagotchi.identityservice.email.application.port.VerificationMailSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RetryingVerificationMailSenderTest {

    private static final String EMAIL = "user@example.com";
    private static final String CODE = "042910";
    private static final String CHALLENGE_ID = "challenge-id";
    private static final Duration VALIDITY = Duration.ofMinutes(10);

    @Mock
    private VerificationMailSender delegate;

    private RetryingVerificationMailSender retryingSender;

    @BeforeEach
    void setUp() {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .backOff(new FixedBackOff(0, 2))
                .predicate(throwable -> throwable instanceof EmailDeliveryException exception
                        && exception.retryable())
                .build();
        RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
        retryingSender = new RetryingVerificationMailSender(delegate, retryTemplate);
    }

    @Test
    @DisplayName("정상 발송 시 위임 객체를 1회만 호출")
    void sendVerificationCode_success_delegatesOnce() {
        retryingSender.sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);

        verify(delegate).sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);
    }

    @Test
    @DisplayName("재시도 가능한 오류 발생 시 최대 설정된 횟수만큼 재시도 후 성공")
    void sendVerificationCode_retryableFailure_retriesAndSucceeds() {
        EmailDeliveryException retryableFailure =
                new EmailDeliveryException(503, "temporary_error", true);

        doThrow(retryableFailure)
                .doThrow(retryableFailure)
                .doNothing()
                .when(delegate)
                .sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);

        retryingSender.sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);

        verify(delegate, times(3)).sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);
    }

    @Test
    @DisplayName("재시도 가능한 오류가 최대 재시도 횟수를 초과하면 최종 예외 발생")
    void sendVerificationCode_retryableFailureExceedsLimit_throwsException() {
        EmailDeliveryException retryableFailure =
                new EmailDeliveryException(503, "temporary_error", true);

        doThrow(retryableFailure)
                .when(delegate)
                .sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);

        assertThatThrownBy(() -> retryingSender.sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY))
                .isInstanceOf(EmailDeliveryException.class)
                .hasFieldOrPropertyWithValue("providerStatusCode", 503);

        verify(delegate, times(3)).sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);
    }

    @Test
    @DisplayName("재시도 불가능한 오류 발생 시 재시도 없이 즉시 예외 전파")
    void sendVerificationCode_nonRetryableFailure_throwsImmediately() {
        EmailDeliveryException nonRetryableFailure =
                new EmailDeliveryException(400, "validation_error", false);

        doThrow(nonRetryableFailure)
                .when(delegate)
                .sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);

        assertThatThrownBy(() -> retryingSender.sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY))
                .isInstanceOf(EmailDeliveryException.class)
                .hasFieldOrPropertyWithValue("providerStatusCode", 400);

        verify(delegate, times(1)).sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);
    }
}
