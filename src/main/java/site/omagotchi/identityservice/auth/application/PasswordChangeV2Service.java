package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationErrorCode;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordChangeV2Service {

    private final PasswordChangeV2Transaction transaction;

    public void changePassword(
            UUID accountId,
            String currentRawPassword,
            String newRawPassword,
            UUID challengeId,
            String code
    ) {
        boolean verified = transaction.changePassword(
                accountId,
                currentRawPassword,
                newRawPassword,
                challengeId,
                code
        );
        if (!verified) {
            throw new BusinessException(EmailVerificationErrorCode.INVALID_CHALLENGE);
        }
    }
}
