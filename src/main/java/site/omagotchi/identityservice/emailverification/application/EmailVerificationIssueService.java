package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.emailverification.application.result.PreparedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;
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

    /** 회원가입용 이메일 OTP를 발급한다. */
    public IssuedEmailVerification issueSignupOtp(String normalizedEmail) {
        return issue(normalizedEmail, EmailVerificationPurpose.SIGNUP);
    }

    /** 비밀번호 변경용 이메일 OTP를 발급한다. */
    public IssuedEmailVerification issuePasswordChangeOtp(String normalizedEmail) {
        return issue(normalizedEmail, EmailVerificationPurpose.PASSWORD_CHANGE);
    }

    /** 비밀번호 재설정용 이메일 OTP를 발급한다. */
    public IssuedEmailVerification issuePasswordResetOtp(String normalizedEmail) {
        return issue(normalizedEmail, EmailVerificationPurpose.PASSWORD_RESET);
    }

    /** 지정된 목적의 OTP를 준비하고 메일 전달 결과를 처리한다. */
    private IssuedEmailVerification issue(
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
            if (deliveryFailure.failureKind() == EmailDeliveryFailureKind.RATE_LIMITED) {
                // Provider 429에서는 외부 요청 증폭을 막기 위해 발급 쿨다운을 유지한다.
                long retryAfterSeconds = compensateRateLimitedDelivery(
                        prepared,
                        deliveryFailure
                );
                if (retryAfterSeconds > 0) {
                    throw new EmailVerificationCooldownException(retryAfterSeconds);
                }
                throw dependencyUnavailable(deliveryFailure);
            }
            compensateDeliveryFailure(prepared, deliveryFailure);
            throw dependencyUnavailable(deliveryFailure);
        }

        boolean currentChallenge = true;
        try {
            currentChallenge = deliveryTransaction.markAccepted(prepared);
        } catch (RuntimeException statusUpdateFailure) {
            // 메일은 이미 접수되었고 PENDING Challenge도 검증할 수 있으므로 202 계약을 유지한다.
            log.error(
                    "인증 메일 접수 상태 기록 실패 challengeId={}",
                    prepared.challengeId(),
                    statusUpdateFailure
            );
        }
        if (!currentChallenge) {
            throw new BusinessException(EmailVerificationErrorCode.ISSUE_SUPERSEDED);
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

    /** 메일 전달 실패 상태를 기록하고 공유 쿨다운을 해제한다. */
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

    /** Provider 요청 제한을 기록하고 유지할 공유 쿨다운의 남은 시간을 반환한다. */
    private long compensateRateLimitedDelivery(
            PreparedEmailVerification prepared,
            EmailDeliveryException deliveryFailure
    ) {
        try {
            return deliveryTransaction.markFailedKeepingCooldown(prepared);
        } catch (RuntimeException compensationFailure) {
            deliveryFailure.addSuppressed(compensationFailure);
            log.error(
                    "Rate Limit 인증 메일 실패 기록 실패 challengeId={}",
                    prepared.challengeId(),
                    compensationFailure
            );
            return 0;
        }
    }

    /** 메일 전달 실패를 외부 의존성 장애 예외로 변환한다. */
    private DependencyUnavailableException dependencyUnavailable(
            EmailDeliveryException deliveryFailure
    ) {
        return new DependencyUnavailableException(
                EmailVerificationErrorCode.DELIVERY_UNAVAILABLE,
                deliveryFailure
        );
    }

    /** 전달 완료 전에 만료된 Challenge의 쿨다운을 보상하고 요청을 거절한다. */
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
