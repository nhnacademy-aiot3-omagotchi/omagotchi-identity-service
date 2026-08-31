package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.account.application.result.AccountRegistrationAttempt;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationErrorCode;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRegistrationV2Service {

    private final AccountRegistrationV2Transaction transaction;

    public Account signUp(
            String email,
            String rawPassword,
            String name,
            UUID challengeId,
            String code
    ) {
        AccountRegistrationAttempt attempt = transaction.signUp(
                email,
                rawPassword,
                name,
                challengeId,
                code
        );
        if (!attempt.emailVerified()) {
            // Transaction 종료 뒤 공개 오류로 변환해 실패 횟수 변경을 Rollback하지 않는다.
            throw new BusinessException(EmailVerificationErrorCode.INVALID_CHALLENGE);
        }
        return attempt.account();
    }
}
