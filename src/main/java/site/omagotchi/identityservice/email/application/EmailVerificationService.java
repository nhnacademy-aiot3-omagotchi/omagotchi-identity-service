package site.omagotchi.identityservice.email.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.application.port.EmailVerificationStorageException;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String CODE_PATTERN = "\\d{6}";

    private final EmailVerificationRepository emailVerificationRepository;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationMailDispatchService mailDispatchService;
    private final EmailVerificationProperties properties;

    public EmailVerificationChallengeResult requestCode(
            String normalizedEmail,
            VerificationPurpose purpose
    ) {
        String requiredEmail = requireNormalizedEmail(normalizedEmail);
        VerificationPurpose requiredPurpose = Objects.requireNonNull(purpose, "purpose");

        String challengeId = UUID.randomUUID().toString();
        String verificationCode = codeGenerator.generate();

        // 재발급 제한 선점과 새 OTP Challenge 저장을 Redis에서 원자적으로 처리한다.
        try {
            EmailVerificationReservationResult reservation =
                    emailVerificationRepository.reserveChallenge(
                            requiredPurpose,
                            requiredEmail,
                            new OtpChallenge(challengeId, verificationCode),
                            properties.codeTtl(),
                            properties.resendCooldown()
                    );
            if (!reservation.reserved()) {
                throw new EmailVerificationCooldownException(
                        reservation.remainingCooldownSeconds()
                );
            }
        } catch (EmailVerificationStorageException exception) {
            // 동기 요청의 Redis 가용성 실패를 안정적인 503 계약으로 확정한다.
            throw unavailable(exception);
        }

        long expiresInSeconds = Math.max(1, properties.codeTtl().toSeconds());
        // 저장한 Challenge의 인증 메일 발송 작업을 비동기 Executor에 접수한다.
        try {
            mailDispatchService.dispatch(
                    requiredPurpose,
                    requiredEmail,
                    verificationCode,
                    challengeId,
                    properties.codeTtl()
            );
        } catch (TaskRejectedException exception) {
            // Executor가 접수를 거부하면 저장한 Challenge를 보상 정리하고 503으로 종료한다.
            deleteChallengeAndCooldownAfterDispatchFailure(
                    requiredPurpose,
                    requiredEmail,
                    challengeId,
                    exception
            );
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            // 접수 과정의 예상 밖 실패도 고아 Challenge를 정리한 뒤 원본을 전파한다.
            deleteChallengeAndCooldownAfterDispatchFailure(
                    requiredPurpose,
                    requiredEmail,
                    challengeId,
                    exception
            );
            throw exception;
        }
        return new EmailVerificationChallengeResult(challengeId, expiresInSeconds);
    }

    public void verifyAndConsumeCode(
            String normalizedEmail,
            VerificationPurpose purpose,
            String challengeId,
            String verificationCode
    ) {
        String requiredEmail = requireNormalizedEmail(normalizedEmail);
        VerificationPurpose requiredPurpose = Objects.requireNonNull(purpose, "purpose");

        if (challengeId == null || challengeId.isBlank()
                || verificationCode == null
                || !verificationCode.matches(CODE_PATTERN)) {
            throw invalidVerification();
        }

        // Redis에서 OTP 검증·실패 횟수 반영·성공 시 소비를 하나의 Lua 실행으로 처리한다.
        OtpVerificationStatus status;
        try {
            status = emailVerificationRepository.verifyAndConsume(
                    requiredPurpose,
                    requiredEmail,
                    challengeId,
                    verificationCode,
                    properties.maximumAttempts()
            );
        } catch (EmailVerificationStorageException exception) {
            // 동기 검증 중 Redis 가용성 실패를 안정적인 503 계약으로 확정한다.
            throw unavailable(exception);
        }
        // Key 미존재·Challenge 불일치·시도 소진은 모두 기존 INVALID 계약으로 통일한다.
        if (status != OtpVerificationStatus.VERIFIED) {
            throw invalidVerification();
        }
    }

    private String requireNormalizedEmail(String email) {
        String requiredEmail = Objects.requireNonNull(email, "normalizedEmail");
        if (requiredEmail.isBlank()
                || !requiredEmail.equals(requiredEmail.trim())
                || !requiredEmail.equals(requiredEmail.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("normalizedEmail은 정규화된 값이어야 합니다.");
        }
        return requiredEmail;
    }

    private BusinessException invalidVerification() {
        return new BusinessException(EmailVerificationErrorCode.INVALID);
    }

    private BusinessException unavailable(Throwable cause) {
        return new BusinessException(
                EmailVerificationErrorCode.UNAVAILABLE,
                cause
        );
    }

    private void deleteChallengeAndCooldownAfterDispatchFailure(
            VerificationPurpose purpose,
            String email,
            String challengeId,
            RuntimeException dispatchFailure
    ) {
        try {
            // 실패한 요청이 소유한 Challenge와 쿨다운만 삭제해 후속 재발급 상태를 보호한다.
            emailVerificationRepository.deleteChallengeAndCooldownIfMatches(
                    purpose,
                    email,
                    challengeId
            );
        } catch (EmailVerificationStorageException cleanupFailure) {
            // 예상 가능한 Redis 정리 실패는 최초 접수 실패에 보존하고 기존 응답 계약을 유지한다.
            dispatchFailure.addSuppressed(cleanupFailure);
            log.error(
                    "메일 발송 작업 접수 실패 후 OTP Challenge·쿨다운 정리 실패 purpose={}",
                    purpose,
                    cleanupFailure
            );
        } catch (RuntimeException cleanupFailure) {
            // 예상 밖 정리 오류는 숨기지 않고 최초 접수 실패를 진단 정보로 보존한다.
            cleanupFailure.addSuppressed(dispatchFailure);
            throw cleanupFailure;
        }
    }
}
