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
            boolean deleted = emailVerificationRepository.deleteChallengeIfMatches(
                    purpose,
                    email,
                    challengeId
            );
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
