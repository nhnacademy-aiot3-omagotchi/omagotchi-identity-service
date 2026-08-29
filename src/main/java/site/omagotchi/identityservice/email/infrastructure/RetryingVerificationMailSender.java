package site.omagotchi.identityservice.email.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryTemplate;
import site.omagotchi.identityservice.email.application.port.VerificationMailSender;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class RetryingVerificationMailSender implements VerificationMailSender {

    private final VerificationMailSender delegate;
    private final RetryTemplate retryTemplate;

    public RetryingVerificationMailSender(
            VerificationMailSender delegate,
            RetryTemplate retryTemplate
    ) {
        this.delegate = delegate;
        this.retryTemplate = retryTemplate;
    }

    @Override
    public void sendVerificationCode(
            String recipient,
            String verificationCode,
            String challengeId,
            Duration validity
    ) {
        AtomicInteger attempts = new AtomicInteger();
        retryTemplate.invoke(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt > 1) {
                log.warn("인증 메일 기술적 재시도 attempt={}", attempt - 1);
            }
            delegate.sendVerificationCode(
                    recipient,
                    verificationCode,
                    challengeId,
                    validity
            );
        });
    }
}
