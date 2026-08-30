package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.EmailPolicy;
import site.omagotchi.identityservice.account.domain.PasswordPolicy;
import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;
import site.omagotchi.identityservice.email.application.EmailVerificationService;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class AccountRegistrationV2Service {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final EmailVerificationService emailVerificationService;

    public EmailVerificationChallengeResult requestEmailOtp(
            String email,
            String rawPassword,
            String name
    ) {
        validateRegistration(email, rawPassword, name);
        String normalizedEmail = EmailPolicy.normalize(email);
        if (accountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_EMAIL);
        }
        return emailVerificationService.requestCode(
                normalizedEmail,
                VerificationPurpose.SIGN_UP
        );
    }

    @Transactional
    public Account signUp(
            String email,
            String rawPassword,
            String name,
            String challengeId,
            String verificationCode
    ) {
        validateRegistration(email, rawPassword, name);

        String passwordHash = passwordHasher.hash(rawPassword);
        Account account = Account.register(email, passwordHash, name);
        Account createdAccount = accountRepository.create(account);
        // DB Commit이 실패하면 OTP만 소모될 수 있는 MVP 한계를 수용한다.
        emailVerificationService.verifyAndConsumeCode(
                createdAccount.getEmail(),
                VerificationPurpose.SIGN_UP,
                challengeId,
                verificationCode
        );
        return createdAccount;
    }

    private void validateRegistration(String email, String rawPassword, String name) {
        // Identity가 소유하는 가입 정책별 공개 거절 Code
        if (!EmailPolicy.isSatisfiedBy(email)) {
            throw new BusinessException(AccountErrorCode.INVALID_EMAIL);
        }
        if (!PasswordPolicy.isSatisfiedBy(rawPassword)) {
            throw new BusinessException(AccountErrorCode.INVALID_PASSWORD);
        }
        if (!Account.isNameValid(name)) {
            throw new BusinessException(AccountErrorCode.INVALID_NAME);
        }
    }
}
