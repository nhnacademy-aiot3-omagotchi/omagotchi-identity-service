package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.result.AccountRegistrationResult;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.EmailPolicy;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationUseService;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRegistrationV2Transaction {

    private final AccountRegistrationService accountRegistrationService;
    private final AccountLifecycleService accountLifecycleService;
    private final AccountRepository accountRepository;
    private final EmailVerificationUseService emailVerificationUseService;
    private final AccountRecoveryPolicy recoveryPolicy;
    private final AccountStatusChangeAuditRecorder accountStatusChangeAuditRecorder;
    private final Clock clock;

    @Transactional
    public Optional<AccountRegistrationResult> signUp(
            String email,
            String rawPassword,
            String name,
            UUID challengeId,
            String code
    ) {
        accountRegistrationService.validateRegistrationInput(email, rawPassword, name);
        String normalizedEmail = EmailPolicy.normalize(email);
        Account existing = accountRepository.lockByEmail(normalizedEmail).orElse(null);
        Instant now = clock.instant();

        boolean recovery = existing != null && existing.getStatus() == AccountStatus.WITHDRAWN;
        if (existing != null && !recovery) {
            // 소비된 가입·복구 인증 요청 재사용의 OTP 오류 우선 판정
            // 가입 성공 뒤에도 유지되는 일회성 소비 계약 보존
            if (!emailVerificationUseService.verifySignupOtp(
                    challengeId,
                    normalizedEmail,
                    code
            )) {
                return Optional.empty();
            }
            throw new BusinessException(AccountErrorCode.DUPLICATE_EMAIL);
        }
        if (recovery && existing.getGlobalRole() != GlobalRole.USER) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_EMAIL);
        }
        if (recovery && !recoveryPolicy.canRecover(existing, now)) {
            throw new BusinessException(AccountErrorCode.PURGE_PENDING);
        }

        boolean verified = recovery
                ? emailVerificationUseService.verifyAccountRecoveryOtp(
                        challengeId,
                        normalizedEmail,
                        code
                )
                : emailVerificationUseService.verifySignupOtp(
                        challengeId,
                        normalizedEmail,
                        code
                );
        if (!verified) {
            // 잘못된 인증번호 실패 횟수의 선행 커밋을 위한 결과 반환
            return Optional.empty();
        }

        if (!recovery) {
            Account account = accountRegistrationService.signUp(email, rawPassword, name);
            emailVerificationUseService.consume(challengeId);
            return Optional.of(new AccountRegistrationResult(
                    account,
                    AccountRegistrationResult.Outcome.CREATED
            ));
        }

        accountLifecycleService.recover(existing, rawPassword, name, now);
        emailVerificationUseService.consume(challengeId);
        accountStatusChangeAuditRecorder.recordRecovery(existing.getId(), now);
        return Optional.of(new AccountRegistrationResult(
                existing,
                AccountRegistrationResult.Outcome.RECOVERED
        ));
    }
}
