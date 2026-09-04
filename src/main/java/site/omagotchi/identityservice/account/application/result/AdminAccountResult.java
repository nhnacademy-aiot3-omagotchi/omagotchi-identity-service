package site.omagotchi.identityservice.account.application.result;

import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;

import java.time.Instant;
import java.util.UUID;

/** 관리자 계정 목록에 필요한 Application 조회 결과. */
public record AdminAccountResult(
        UUID accountId,
        String email,
        String name,
        GlobalRole role,
        AccountStatus status,
        short failedLoginAttempts,
        boolean locked,
        Instant lockedUntil,
        Instant statusChangedAt,
        Instant recoveryDeadline,
        Instant createdAt
) {

    public static AdminAccountResult from(
            Account account,
            Instant checkedAt,
            Instant recoveryDeadline
    ) {
        return new AdminAccountResult(
                account.getId(),
                account.getEmail(),
                account.getName(),
                account.getGlobalRole(),
                account.getStatus(),
                account.getFailedLoginAttempts(),
                account.isLoginLockedAt(checkedAt),
                account.getLockedUntil(),
                account.getStatusChangedAt(),
                recoveryDeadline,
                account.getCreatedAt()
        );
    }
}
