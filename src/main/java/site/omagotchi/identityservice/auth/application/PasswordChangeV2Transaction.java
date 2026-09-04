package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationUseService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordChangeV2Transaction {

    private final AccountPasswordService accountPasswordService;
    private final RefreshSessionRevocationService refreshSessionRevocationService;
    private final EmailVerificationUseService emailVerificationUseService;

    @Transactional
    public boolean changePassword(
            UUID accountId,
            String currentRawPassword,
            String newRawPassword,
            UUID challengeId,
            String code
    ) {
        // Account → Challenge → RefreshToken 잠금 순서를 유지한다.
        String accountEmail = accountPasswordService.lockPasswordChangeEmail(accountId);
        boolean verified = emailVerificationUseService.verifyPasswordChangeOtp(
                challengeId,
                accountEmail,
                code
        );
        if (!verified) {
            return false;
        }

        accountPasswordService.verifyAndReplacePasswordHash(
                accountId,
                currentRawPassword,
                newRawPassword
        );
        refreshSessionRevocationService.revokeAllForAccount(
                accountId,
                RefreshSessionRevocationReason.PASSWORD_CHANGED
        );
        emailVerificationUseService.consume(challengeId);
        return true;
    }
}
