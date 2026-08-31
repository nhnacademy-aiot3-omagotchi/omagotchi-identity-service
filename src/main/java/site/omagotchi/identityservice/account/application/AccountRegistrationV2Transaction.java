package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.result.AccountRegistrationAttempt;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.EmailPolicy;
import site.omagotchi.identityservice.emailverification.application.SignupEmailOtpService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRegistrationV2Transaction {

    private final AccountRegistrationService accountRegistrationService;
    private final SignupEmailOtpService emailOtpService;

    @Transactional
    public AccountRegistrationAttempt signUp(
            String email,
            String rawPassword,
            String name,
            UUID challengeId,
            String code
    ) {
        accountRegistrationService.validateRegistrationInput(email, rawPassword, name);
        String normalizedEmail = EmailPolicy.normalize(email);
        boolean verified = emailOtpService.verify(
                challengeId,
                normalizedEmail,
                code
        );
        if (!verified) {
            // 예외 대신 결과를 반환해 잘못된 번호의 실패 횟수를 먼저 Commit한다.
            return AccountRegistrationAttempt.verificationFailed();
        }

        Account account = accountRegistrationService.signUp(email, rawPassword, name);
        emailOtpService.consume(challengeId);
        return AccountRegistrationAttempt.succeeded(account);
    }
}
