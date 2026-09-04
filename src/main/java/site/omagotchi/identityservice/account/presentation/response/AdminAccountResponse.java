package site.omagotchi.identityservice.account.presentation.response;

import site.omagotchi.identityservice.account.application.result.AdminAccountResult;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;

import java.time.Instant;
import java.util.UUID;

/** 관리자 목록의 계정 한 건이다. 비밀번호 Hash 등 인증 근거값은 포함하지 않는다. */
public record AdminAccountResponse(
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

    public static AdminAccountResponse from(AdminAccountResult account) {
        return new AdminAccountResponse(
                account.accountId(),
                account.email(),
                account.name(),
                account.role(),
                account.status(),
                account.failedLoginAttempts(),
                account.locked(),
                account.lockedUntil(),
                account.statusChangedAt(),
                account.recoveryDeadline(),
                account.createdAt()
        );
    }
}
