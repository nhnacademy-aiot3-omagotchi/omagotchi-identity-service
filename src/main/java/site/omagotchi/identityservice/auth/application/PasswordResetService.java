package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.auth.application.result.PasswordResetEmailOtpResult;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationIssueService;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTransaction transaction;
    private final AccountPasswordService accountPasswordService;
    private final EmailVerificationIssueService emailVerificationIssueService;

    public PasswordResetEmailOtpResult issueEmailOtp(String email) {
        // 이메일 검증 및 정규화
        String normalizedEmail = accountPasswordService
                .validateAndNormalizePasswordResetEmail(email);
        // 이메일 otp 발급
        IssuedEmailVerification issued = emailVerificationIssueService
                .issuePasswordResetOtp(normalizedEmail);
        return new PasswordResetEmailOtpResult(
                issued.challengeId(),
                issued.expiresInSeconds()
        );
    }

    /** 재설정 요청을 검증하고 계정·OTP 관련 거절을 단일 비즈니스 오류로 변환한다. */
    public void resetPassword(
            String email,
            String newRawPassword,
            UUID challengeId,
            String code
    ) {
        String normalizedEmail = accountPasswordService
                .validateAndNormalizePasswordResetEmail(email);
        accountPasswordService.validatePasswordResetPassword(newRawPassword);

        boolean reset = transaction.resetPassword(
                normalizedEmail,
                newRawPassword,
                challengeId,
                code
        );
        if (!reset) {
            throw new BusinessException(AuthErrorCode.INVALID_PASSWORD_RESET);
        }
    }
}
