package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.AccountQueryService;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordChangeEmailOtpService {

    private final AccountQueryService accountQueryService;
    private final EmailVerificationIssueService issueService;

    public IssuedEmailVerification issue(UUID accountId) {
        Account account = accountQueryService.getById(accountId);
        if (!account.isPasswordChangeAllowed()) {
            throw new BusinessException(AccountErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
        }
        return issueService.issue(account.getEmail(), EmailVerificationPurpose.PASSWORD_CHANGE);
    }
}
