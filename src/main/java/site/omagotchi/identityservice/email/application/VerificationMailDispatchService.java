package site.omagotchi.identityservice.email.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.email.application.port.EmailDeliveryException;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.application.port.VerificationMailSender;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationMailDispatchService {

    // email-mvp: 전달 보장이 필요해지면 webhook, outbox, MQ 또는 DLQ를 검토한다.

    private final VerificationMailSender mailSender;
    private final EmailVerificationRepository emailVerificationRepository;
    private final RetryTemplate verificationMailRetryTemplate;

    @Async("mailTaskExecutor")
    public void dispatch(
            VerificationPurpose purpose,
            String email,
            String verificationCode,
            String challengeId,
            Duration validity
    ) {
        AtomicInteger attempts = new AtomicInteger();
        try {
            verificationMailRetryTemplate.invoke(() -> {
                int attempt = attempts.incrementAndGet();
                if (attempt > 1) {
                    log.warn("인증 메일 기술적 재시도 attempt={}", attempt - 1);
                }
                mailSender.sendVerificationCode(
                        email,
                        verificationCode,
                        challengeId,
                        validity
                );
            });
        } catch (EmailDeliveryException exception) {
            boolean deleted = emailVerificationRepository.deleteChallengeIfMatches(
                    purpose,
                    email,
                    challengeId
            );
            log.error(
                    "인증 메일 최종 실패 retries={}, statusCode={}, errorName={}, activeChallengeDeleted={}",
                    Math.max(0, attempts.get() - 1),
                    exception.providerStatusCode(),
                    exception.providerErrorName(),
                    deleted,
                    exception
            );
        }
    }
}
