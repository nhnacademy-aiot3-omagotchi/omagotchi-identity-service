package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.AccountRegistrationService;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.EmailPolicy;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class SignupEmailOtpService {

    private final AccountRepository accountRepository;
    private final AccountRegistrationService accountRegistrationService;
    private final EmailVerificationIssueService issueService;

    public IssuedEmailVerification issue(
            String email,
            String rawPassword,
            String name
    ) {
        accountRegistrationService.validateRegistrationInput(email, rawPassword, name);
        String normalizedEmail = EmailPolicy.normalize(email);
        if (accountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_EMAIL);
        }
        return issueService.issue(normalizedEmail, EmailVerificationPurpose.SIGNUP);
    }
}
