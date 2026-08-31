package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationUseService;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordChangeV2Transaction {

    private final AccountRepository accountRepository;
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
        // 기존 ADR 0002의 Account → RefreshToken 잠금 순서를 유지한다.
        Account account = accountRepository.lockById(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.NOT_FOUND));
        if (!account.isPasswordChangeAllowed()) {
            throw new BusinessException(AccountErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
        }
        boolean verified = emailVerificationUseService.verify(
                challengeId,
                account.getEmail(),
                EmailVerificationPurpose.PASSWORD_CHANGE,
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
