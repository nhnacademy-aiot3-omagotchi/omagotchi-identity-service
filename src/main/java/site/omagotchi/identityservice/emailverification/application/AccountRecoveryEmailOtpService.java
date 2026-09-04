package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRecoveryEmailOtpService {

    private final EmailVerificationIssueService issueService;
    private final EmailVerificationUseService useService;

    public IssuedEmailVerification issue(String normalizedEmail) {
        return issueService.issue(normalizedEmail, EmailVerificationPurpose.ACCOUNT_RECOVERY);
    }

    public boolean verify(UUID challengeId, String normalizedEmail, String code) {
        return useService.verify(
                challengeId,
                normalizedEmail,
                EmailVerificationPurpose.ACCOUNT_RECOVERY,
                code
        );
    }

    public void consume(UUID challengeId) {
        useService.consume(challengeId);
    }
}
