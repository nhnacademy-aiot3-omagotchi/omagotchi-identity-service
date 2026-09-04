package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.result.AccountRegistrationResult;
import site.omagotchi.identityservice.account.application.result.SignupEmailOtpResult;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.EmailPolicy;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.emailverification.application.AccountRecoveryEmailOtpService;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationErrorCode;
import site.omagotchi.identityservice.emailverification.application.SignupEmailOtpService;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRegistrationV2Service {

    private final AccountRegistrationV2Transaction transaction;
    private final AccountRegistrationService accountRegistrationService;
    private final AccountRepository accountRepository;
    private final SignupEmailOtpService emailOtpService;
    private final AccountRecoveryEmailOtpService recoveryEmailOtpService;
    private final AccountRecoveryPolicy recoveryPolicy;
    private final Clock clock;

    public SignupEmailOtpResult issueEmailOtp(
            String email,
            String rawPassword,
            String name
    ) {
        accountRegistrationService.validateRegistrationInput(email, rawPassword, name);
        String normalizedEmail = EmailPolicy.normalize(email);
        Account account = accountRepository.findByEmail(normalizedEmail).orElse(null);
        Instant now = clock.instant();
        IssuedEmailVerification issued;
        if (account == null) {
            issued = emailOtpService.issue(normalizedEmail);
        } else if (account.getStatus() == AccountStatus.WITHDRAWN
                && account.getGlobalRole() == GlobalRole.USER) {
            if (!recoveryPolicy.canRecover(account, now)) {
                throw new BusinessException(AccountErrorCode.PURGE_PENDING);
            }
            issued = recoveryEmailOtpService.issue(normalizedEmail);
        } else {
            throw new BusinessException(AccountErrorCode.DUPLICATE_EMAIL);
        }
        return new SignupEmailOtpResult(issued.challengeId(), issued.expiresInSeconds());
    }

    public AccountRegistrationResult signUp(
            String email,
            String rawPassword,
            String name,
            UUID challengeId,
            String code
    ) {
        // 트랜잭션 종료 후 오류 변환으로 인증 실패 횟수 변경 보존
        return transaction.signUp(
                email,
                rawPassword,
                name,
                challengeId,
                code
        ).orElseThrow(() -> new BusinessException(
                EmailVerificationErrorCode.INVALID_CHALLENGE
        ));
    }
}
