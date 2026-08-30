package site.omagotchi.identityservice.email.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.email.application.port.EmailDeliveryException;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.application.port.EmailVerificationStorageException;
import site.omagotchi.identityservice.email.application.port.VerificationMailSender;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationMailDispatchService {

    // email-mvp: 전달 보장이 필요해지면 webhook, outbox, MQ 또는 DLQ를 검토한다.

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
        try {
            mailSender.sendVerificationCode(
                    email,
                    verificationCode,
                    challengeId,
                    validity
            );
        } catch (EmailDeliveryException exception) {
            boolean deleted;
            // 재시도를 모두 소진한 메일 발송 실패는 현재 활성 Challenge를 보상 삭제한다.
            try {
                deleted = emailVerificationRepository.deleteChallengeIfMatches(
                        purpose,
                        email,
                        challengeId
                );
            } catch (EmailVerificationStorageException cleanupException) {
                // 정리 저장소 장애는 비HTTP 경계에서 메일 실패와 함께 기록하고 격리한다.
                cleanupException.addSuppressed(exception);
                log.error(
                        "인증 메일 최종 실패 후 OTP 정리 실패 statusCode={}, errorName={}",
                        exception.providerStatusCode(),
                        exception.providerErrorName(),
                        cleanupException
                );
                return;
            }
            log.error(
                    "인증 메일 최종 실패 statusCode={}, errorName={}, activeChallengeDeleted={}",
                    exception.providerStatusCode(),
                    exception.providerErrorName(),
                    deleted,
                    exception
            );
        }
    }
}
