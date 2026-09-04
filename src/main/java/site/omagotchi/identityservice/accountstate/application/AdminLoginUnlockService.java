package site.omagotchi.identityservice.accountstate.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountAdministrationService;
import site.omagotchi.identityservice.account.application.AccountStatusChangeAuditRecorder;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminLoginUnlockService {

    private final AccountAdministrationService accountAdministrationService;
    private final AccountStatusChangeAuditRecorder accountStatusChangeAuditRecorder;

    @Transactional
    public void unlockLogin(UUID actorAccountId, UUID targetAccountId, String reason) {
        accountAdministrationService
                .unlockLogin(actorAccountId, targetAccountId)
                .ifPresent(unlockedAt ->
                        accountStatusChangeAuditRecorder.recordLoginLockReleased(
                                actorAccountId,
                                targetAccountId,
                                reason,
                                unlockedAt
                        )
                );
    }
}
