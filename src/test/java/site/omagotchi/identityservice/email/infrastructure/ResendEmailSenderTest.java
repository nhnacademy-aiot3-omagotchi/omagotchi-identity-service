package site.omagotchi.identityservice.email.infrastructure;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.email.application.port.EmailDeliveryException;
import site.omagotchi.identityservice.email.infrastructure.config.ResendProperties;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ResendEmailSenderTest {

    private static final String API_KEY = "test-only-resend-api-key";
    private static final String FROM_EMAIL = "Omagotchi <no-reply@example.com>";
    private static final String RECIPIENT = "user@example.com";
    private static final String VERIFICATION_CODE = "123456";
    private static final String CHALLENGE_ID =
            "00000000-0000-0000-0000-000000000001";
    private static final Duration VALIDITY = Duration.ofMinutes(10);

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    private ResendEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new ResendEmailSender(
                resend,
                new ResendProperties(
                        API_KEY,
                        FROM_EMAIL
                )
        );
    }

    @Test
    @DisplayName("Challenge ID를 Resend Idempotency Key로 사용하는 plain text 메일 조립")
    void sendsVerificationCodeWithChallengeIdempotencyKey() throws ResendException {
        given(resend.emails()).willReturn(emails);
        ArgumentCaptor<CreateEmailOptions> optionsCaptor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        ArgumentCaptor<RequestOptions> requestOptionsCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);

        sendVerificationCode();

        verify(emails).send(optionsCaptor.capture(), requestOptionsCaptor.capture());
        CreateEmailOptions options = optionsCaptor.getValue();
        RequestOptions requestOptions = requestOptionsCaptor.getValue();
        thenSoftly(softly -> {
            softly.then(options.getFrom()).isEqualTo(FROM_EMAIL);
            softly.then(options.getTo()).containsExactly(RECIPIENT);
            softly.then(options.getSubject()).isEqualTo("[Omagotchi] 이메일 인증 코드");
            softly.then(options.getText())
                    .contains("인증 코드: " + VERIFICATION_CODE)
                    .contains("유효 시간: 10분")
                    .doesNotContain(API_KEY);
            softly.then(options.getHtml()).isNull();
            softly.then(requestOptions.getIdempotencyKey()).isEqualTo(CHALLENGE_ID);
        });
    }

    @Test
    @DisplayName("Resend 5xx 오류를 재시도 가능한 안전한 예외로 변환")
    void convertsServerFailureToRetryableException() throws ResendException {
        givenDeliveryFailure(new ResendException(
                503,
                "{\"name\":\"internal_server_error\",\"message\":\"provider-failure\"}"
        ));

        Throwable thrown = catchThrowable(this::sendVerificationCode);

        then(thrown).isInstanceOfSatisfying(
                EmailDeliveryException.class,
                exception -> thenSoftly(softly -> {
                    softly.then(exception.getMessage())
                            .isEqualTo("이메일 발송 제공자 호출에 실패했습니다.")
                            .doesNotContain(RECIPIENT)
                            .doesNotContain(VERIFICATION_CODE)
                            .doesNotContain("provider-failure");
                    softly.then(exception.providerStatusCode()).isEqualTo(503);
                    softly.then(exception.providerErrorName())
                            .isEqualTo("internal_server_error");
                    softly.then(exception.retryable()).isTrue();
                    softly.then(exception.getCause()).isInstanceOf(ResendException.class);
                })
        );
    }

    @Test
    @DisplayName("네트워크 timeout을 재시도 가능한 안전한 예외로 변환")
    void convertsTimeoutToRetryableException() throws ResendException {
        givenDeliveryFailure(
                new RuntimeException(new SocketTimeoutException("timed-out"))
        );

        Throwable thrown = catchThrowable(this::sendVerificationCode);

        then(thrown).isInstanceOfSatisfying(
                EmailDeliveryException.class,
                exception -> thenSoftly(softly -> {
                    softly.then(exception.providerStatusCode()).isNull();
                    softly.then(exception.providerErrorName()).isEqualTo("network_error");
                    softly.then(exception.retryable()).isTrue();
                    softly.then(exception.getCause()).isInstanceOf(RuntimeException.class);
                    softly.then(exception.getCause().getCause())
                            .isInstanceOf(SocketTimeoutException.class);
                })
        );
    }

    @Test
    @DisplayName("Resend 400 validation 오류를 재시도 불가능한 예외로 변환")
    void convertsValidationFailureToNonRetryableException() throws ResendException {
        givenDeliveryFailure(new ResendException(
                400,
                "{\"name\":\"validation_error\",\"message\":\"invalid\"}"
        ));

        Throwable thrown = catchThrowable(this::sendVerificationCode);

        then(thrown).isInstanceOfSatisfying(
                EmailDeliveryException.class,
                exception -> thenSoftly(softly -> {
                    softly.then(exception.providerStatusCode()).isEqualTo(400);
                    softly.then(exception.providerErrorName()).isEqualTo("validation_error");
                    softly.then(exception.retryable()).isFalse();
                    softly.then(exception.getCause()).isInstanceOf(ResendException.class);
                })
        );
    }

    @Test
    @DisplayName("Resend 초당 요청 제한 429 오류를 재시도 가능한 예외로 변환")
    void convertsRateLimitFailureToRetryableException() throws ResendException {
        givenDeliveryFailure(new ResendException(
                429,
                "{\"name\":\"rate_limit_exceeded\",\"message\":\"too many requests\"}"
        ));

        Throwable thrown = catchThrowable(this::sendVerificationCode);

        then(thrown).isInstanceOfSatisfying(
                EmailDeliveryException.class,
                exception -> thenSoftly(softly -> {
                    softly.then(exception.providerStatusCode()).isEqualTo(429);
                    softly.then(exception.providerErrorName()).isEqualTo("rate_limit_exceeded");
                    softly.then(exception.retryable()).isTrue();
                })
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"daily_quota_exceeded", "monthly_quota_exceeded"})
    @DisplayName("Resend quota 초과 429 오류를 재시도 불가능한 예외로 변환")
    void convertsQuotaFailureToNonRetryableException(String errorName) throws ResendException {
        givenDeliveryFailure(new ResendException(
                429,
                "{\"name\":\"%s\",\"message\":\"quota exceeded\"}".formatted(errorName)
        ));

        Throwable thrown = catchThrowable(this::sendVerificationCode);

        then(thrown).isInstanceOfSatisfying(
                EmailDeliveryException.class,
                exception -> thenSoftly(softly -> {
                    softly.then(exception.providerStatusCode()).isEqualTo(429);
                    softly.then(exception.providerErrorName()).isEqualTo(errorName);
                    softly.then(exception.retryable()).isFalse();
                })
        );
    }

    @Test
    @DisplayName("6자리 숫자가 아닌 인증 코드는 Resend 호출 전 거절")
    void rejectsMalformedVerificationCode() {
        Throwable thrown = catchThrowable(() -> sender.sendVerificationCode(
                RECIPIENT,
                "12345",
                CHALLENGE_ID,
                VALIDITY
        ));

        then(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("verificationCode는 6자리 숫자여야 합니다.");
        verifyNoInteractions(resend);
    }

    private void givenDeliveryFailure(Throwable failure) throws ResendException {
        given(resend.emails()).willReturn(emails);
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willThrow(failure);
    }

    private void sendVerificationCode() {
        sender.sendVerificationCode(
                RECIPIENT,
                VERIFICATION_CODE,
                CHALLENGE_ID,
                VALIDITY
        );
    }
}
