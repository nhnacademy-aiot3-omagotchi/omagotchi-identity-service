package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.result.AccountRegistrationAttempt;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.EmailPolicy;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationUseService;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRegistrationV2Transaction {

    private final AccountRegistrationService accountRegistrationService;
    private final EmailVerificationUseService emailVerificationUseService;
    private final Clock clock;

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
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean verified = emailVerificationUseService.verify(
                challengeId,
                normalizedEmail,
                EmailVerificationPurpose.SIGNUP,
                code,
                now
        );
        if (!verified) {
            // 예외 대신 결과를 반환해 잘못된 번호의 실패 횟수를 먼저 Commit한다.
            return AccountRegistrationAttempt.verificationFailed();
        }

        Account account = accountRegistrationService.signUp(email, rawPassword, name);
        emailVerificationUseService.consume(challengeId, now);
        return AccountRegistrationAttempt.succeeded(account);
    }
}
