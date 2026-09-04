package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.auth.application.result.PasswordChangeEmailOtpResult;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationErrorCode;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationIssueService;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordChangeV2Service {

    private final PasswordChangeV2Transaction transaction;
    private final AccountPasswordService accountPasswordService;
    private final EmailVerificationIssueService emailVerificationIssueService;

    public PasswordChangeEmailOtpResult issueEmailOtp(UUID accountId) {
        String email = accountPasswordService.getPasswordChangeEmail(accountId);
        IssuedEmailVerification issued = emailVerificationIssueService
                .issuePasswordChangeOtp(email);
        return new PasswordChangeEmailOtpResult(issued.challengeId(), issued.expiresInSeconds());
    }

    public void changePassword(
            UUID accountId,
            String currentRawPassword,
            String newRawPassword,
            UUID challengeId,
            String code
    ) {
        boolean verified = transaction.changePassword(
                accountId,
                currentRawPassword,
                newRawPassword,
                challengeId,
                code
        );
        if (!verified) {
            throw new BusinessException(EmailVerificationErrorCode.INVALID_CHALLENGE);
        }
    }
}
