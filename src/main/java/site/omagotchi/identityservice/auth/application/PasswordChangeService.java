package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.account.application.AccountQueryService;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;
import site.omagotchi.identityservice.email.application.EmailVerificationService;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

/**
 * 인증된 사용자의 비밀번호 변경과 모든 Refresh Session 폐기를 하나의 Transaction으로 수행한다.
 */
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final AccountPasswordService accountPasswordService;
    private final RefreshSessionRevocationService refreshSessionRevocationService;
    private final AccountQueryService accountQueryService;
    private final EmailVerificationService emailVerificationService;

    public EmailVerificationChallengeResult requestEmailOtp(UUID accountId) {
        Account account = accountQueryService.getById(accountId);
        if (!account.isPasswordChangeAllowed()) {
            throw new BusinessException(AccountErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
        }
        return emailVerificationService.requestCode(
                account.getEmail(),
                VerificationPurpose.PASSWORD_CHANGE
        );
    }

    @Transactional
    public void changePassword(
            UUID accountId,
            String currentRawPassword,
            String newRawPassword,
            String challengeId,
            String verificationCode
    ) {
        String accountEmail = accountPasswordService.verifyAndReplacePasswordHash(
                accountId,
                currentRawPassword,
                newRawPassword
        );
        refreshSessionRevocationService.revokeAllForAccount(
                accountId,
                RefreshSessionRevocationReason.PASSWORD_CHANGED
        );
        // DB Commit이 실패하면 OTP만 소모될 수 있는 MVP 한계를 수용한다.
        emailVerificationService.verifyAndConsumeCode(
                accountEmail,
                VerificationPurpose.PASSWORD_CHANGE,
                challengeId,
                verificationCode
        );
    }
}
