package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountPasswordService;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationUseService;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetTransaction {

    private final AccountPasswordService accountPasswordService;
    private final RefreshSessionRevocationService refreshSessionRevocationService;
    private final EmailVerificationUseService emailVerificationUseService;

    /** 계정 잠금부터 비밀번호 교체·세션 폐기·OTP 소비까지 하나의 트랜잭션으로 실행한다. */
    @Transactional
    public boolean resetPassword(
            String normalizedEmail,
            String newRawPassword,
            UUID challengeId,
            String code
    ) {
        // Account → Challenge → RefreshToken 잠금 순서를 유지한다.
        Optional<UUID> accountId = accountPasswordService.lockPasswordResetAccountId(
                normalizedEmail
        );
        boolean verified = emailVerificationUseService.verifyPasswordResetOtp(
                challengeId,
                normalizedEmail,
                code
        );
        if (!verified) {
            return false;
        }
        if (accountId.isEmpty()) {
            // 계정이 나중에 같은 이메일로 생성·활성화되어도 OTP를 재사용할 수 없게 한다.
            emailVerificationUseService.consume(challengeId);
            return false;
        }

        UUID targetAccountId = accountId.get();
        boolean passwordReplaced = accountPasswordService.replacePasswordHashForReset(
                targetAccountId,
                newRawPassword
        );
        if (!passwordReplaced) {
            return false;
        }
        refreshSessionRevocationService.revokeAllForAccount(
                targetAccountId,
                RefreshSessionRevocationReason.PASSWORD_RESET
        );
        emailVerificationUseService.consume(challengeId);
        return true;
    }
}
