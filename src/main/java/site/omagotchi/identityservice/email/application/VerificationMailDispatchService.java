package site.omagotchi.identityservice.email.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.email.application.port.EmailDeliveryException;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.application.port.VerificationMailSender;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationMailDispatchService {

    // 최대 재시도 횟수
    private static final int MAX_TECHNICAL_RETRIES = 2;

    // TODO(email-mvp): 전달 보장이 필요해지면 webhook, outbox, MQ 또는 DLQ를 검토한다.

    private final VerificationMailSender mailSender;
    private final EmailVerificationRepository emailVerificationRepository;

    @Async("mailTaskExecutor")
    public void dispatch(
            VerificationPurpose purpose,
            String email,
            String verificationCode,
            String challengeId,
            Duration validity
    ) {
        int retries = 0;
        while (true) {
            try {
                mailSender.sendVerificationCode(
                        email,
                        verificationCode,
                        challengeId,
                        validity
                );
                return;
            } catch (EmailDeliveryException exception) {
                if (exception.retryable() && retries < MAX_TECHNICAL_RETRIES) {
                    retries++;
                    log.warn(
                            "Resend OTP 메일 기술적 재시도 attempt={}, statusCode={}, errorName={}",
                            retries,
                            exception.providerStatusCode(),
                            exception.providerErrorName()
                    );
                    continue;
                }

                boolean deleted = emailVerificationRepository.deleteChallengeIfMatches(
                        purpose,
                        email,
                        challengeId
                );
                log.error(
                        "Resend OTP 메일 최종 실패 retries={}, statusCode={}, errorName={}, activeChallengeDeleted={}",
                        retries,
                        exception.providerStatusCode(),
                        exception.providerErrorName(),
                        deleted
                );
                return;
            }
        }
    }
}
