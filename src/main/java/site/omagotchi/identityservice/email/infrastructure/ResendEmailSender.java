package site.omagotchi.identityservice.email.infrastructure;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.email.application.port.EmailDeliveryException;
import site.omagotchi.identityservice.email.application.port.VerificationMailSender;
import site.omagotchi.identityservice.email.infrastructure.config.ResendProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ResendEmailSender implements VerificationMailSender {

    private static final String SUBJECT = "[Omagotchi] 이메일 인증 코드";
    private static final String CODE_PATTERN = "\\d{6}";
    private static final int TOO_MANY_REQUESTS = 429;
    private static final String RATE_LIMIT_EXCEEDED = "rate_limit_exceeded";

    private final Resend resend;
    private final ResendProperties properties;

    @Override
    public void sendVerificationCode(
            String recipient,
            String verificationCode,
            String challengeId,
            Duration validity
    ) {
        String requiredRecipient = requireText(recipient, "recipient");
        String requiredCode = requireVerificationCode(verificationCode);
        String requiredChallengeId = requireText(challengeId, "challengeId");
        Duration requiredValidity = requirePositive(validity);

        // Resend에서 제공하는 이메일 생성 옵션
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(properties.fromEmail())                   // 발신자
                .to(requiredRecipient)                          // 수신자
                .subject(SUBJECT)                               // 제목
                .text(body(requiredCode, requiredValidity))     // 이메일 형식
                .build();
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(requiredChallengeId)
                .build();

        try {
            resend.emails().send(options, requestOptions);
        } catch (ResendException exception) {
            throw new EmailDeliveryException(
                    exception.getStatusCode(),
                    exception.getErrorName(),
                    isRetryable(exception.getStatusCode(), exception.getErrorName()),
                    exception
            );
        } catch (RuntimeException exception) {
            if (hasIoCause(exception)) {
                throw new EmailDeliveryException(null, "network_error", true, exception);
            }
            throw exception;
        }
    }

    private boolean isRetryable(Integer statusCode, String errorName) {
        if (statusCode == null) {
            return false;
        }
        if (statusCode >= 500 && statusCode < 600) {
            return true;
        }

        // TODO: retry-After 헤더를 받을 수 있는 구조로 변경되면, provider-neutral한 재시도 지연으로 변환하여 적용한다.
        return statusCode == TOO_MANY_REQUESTS && RATE_LIMIT_EXCEEDED.equals(errorName);
    }

    private boolean hasIoCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // TODO: 추후 이메일 전송 바디 템플릿을 생성해야 한다. 최소 MVP에서는 코드를 전송하는 것을 목표로 한다.
    private String body(String verificationCode, Duration validity) {
        long validityMinutes = Math.max(1, validity.toMinutes());
        return """
                Omagotchi 이메일 인증 코드입니다.

                인증 코드: %s
                유효 시간: %d분

                본인이 요청하지 않았다면 이 메일을 무시하세요.
                """.formatted(verificationCode, validityMinutes);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 비어 있을 수 없습니다.");
        }
        return value;
    }

    private String requireVerificationCode(String verificationCode) {
        String requiredCode = requireText(verificationCode, "verificationCode");
        if (!requiredCode.matches(CODE_PATTERN)) {
            throw new IllegalArgumentException("verificationCode는 6자리 숫자여야 합니다.");
        }
        return requiredCode;
    }

    private Duration requirePositive(Duration validity) {
        Duration requiredValidity = Objects.requireNonNull(validity, "validity");
        if (requiredValidity.isZero() || requiredValidity.isNegative()) {
            throw new IllegalArgumentException("validity는 0보다 커야 합니다.");
        }
        return requiredValidity;
    }
}
