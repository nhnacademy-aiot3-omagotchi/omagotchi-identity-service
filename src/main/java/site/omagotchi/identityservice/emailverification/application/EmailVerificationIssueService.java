package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.emailverification.application.result.PreparedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.global.exception.DependencyUnavailableException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationIssueService {

    private final EmailVerificationIssuanceTransaction issuanceTransaction;
    private final EmailVerificationDeliveryTransaction deliveryTransaction;
    private final EmailVerificationMailSender mailSender;
    private final EmailVerificationProperties properties;
    private final Clock clock;

    public IssuedEmailVerification issue(
            String normalizedEmail,
            EmailVerificationPurpose purpose
    ) {
        PreparedEmailVerification prepared = issuanceTransaction.prepare(
                normalizedEmail,
                purpose
        );

        try {
            mailSender.sendVerificationCode(
                    prepared.challengeId(),
                    prepared.email(),
                    prepared.code(),
                    properties.codeTtl()
            );
        } catch (EmailDeliveryException deliveryFailure) {
            compensateDeliveryFailure(prepared, deliveryFailure);
            throw new DependencyUnavailableException(
                    EmailVerificationErrorCode.DELIVERY_UNAVAILABLE,
                    deliveryFailure
            );
        }

        try {
            deliveryTransaction.markAccepted(prepared.challengeId());
        } catch (RuntimeException statusUpdateFailure) {
            // 메일은 이미 접수되었고 PENDING Challenge도 검증할 수 있으므로 202 계약을 유지한다.
            log.error(
                    "인증 메일 접수 상태 기록 실패 challengeId={}",
                    prepared.challengeId(),
                    statusUpdateFailure
            );
        }

        Instant responseAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!responseAt.isBefore(prepared.expiresAt())) {
            rejectExpiredDelivery(prepared);
        }

        long expiresInSeconds = Math.max(1, Duration.between(
                responseAt,
                prepared.expiresAt()
        ).toSeconds());
        return new IssuedEmailVerification(prepared.challengeId(), expiresInSeconds);
    }

    private void compensateDeliveryFailure(
            PreparedEmailVerification prepared,
            EmailDeliveryException deliveryFailure
    ) {
        try {
            deliveryTransaction.markFailedAndReleaseCooldown(prepared);
        } catch (RuntimeException compensationFailure) {
            deliveryFailure.addSuppressed(compensationFailure);
            log.error(
                    "인증 메일 실패 보상 기록 실패 challengeId={}",
                    prepared.challengeId(),
                    compensationFailure
            );
        }
    }

    private void rejectExpiredDelivery(PreparedEmailVerification prepared) {
        EmailDeliveryException deadlineFailure = new EmailDeliveryException(
                "인증 메일 처리 중 Challenge 유효시간이 만료되었습니다.",
                new IllegalStateException("메일 사업자 응답이 Challenge 만료 시각을 초과했습니다.")
        );
        try {
            // 사업자가 요청을 접수했을 수 있으므로 전달 상태는 되돌리지 않고 재발급만 허용한다.
            deliveryTransaction.releaseCooldown(prepared);
        } catch (RuntimeException compensationFailure) {
            deadlineFailure.addSuppressed(compensationFailure);
            log.error(
                    "만료된 인증 메일의 쿨다운 해제 실패 challengeId={}",
                    prepared.challengeId(),
                    compensationFailure
            );
        }
        throw new DependencyUnavailableException(
                EmailVerificationErrorCode.DELIVERY_UNAVAILABLE,
                deadlineFailure
        );
    }
}
