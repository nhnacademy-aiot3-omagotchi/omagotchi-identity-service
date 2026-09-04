package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AccountRecoveryPolicy {

    public static final Duration RECOVERY_WINDOW = Duration.ofDays(30);

    private final AccountRecoveryProperties properties;

    public Instant recoveryDeadline(Account account) {
        Objects.requireNonNull(account, "account");
        if (account.getStatus() != AccountStatus.WITHDRAWN) {
            throw new IllegalArgumentException("탈퇴 계정만 복구 기한을 계산할 수 있습니다.");
        }

        return recoveryDeadline(account.getStatusChangedAt());
    }

    public Instant recoveryDeadline(Instant statusChangedAt) {
        Instant changedAt = Objects.requireNonNull(statusChangedAt, "statusChangedAt");

        Instant baseline = changedAt.isAfter(properties.policyEffectiveAt())
                ? changedAt
                : properties.policyEffectiveAt();
        return baseline.plus(RECOVERY_WINDOW);
    }

    public boolean canRecover(Account account, Instant now) {
        return Objects.requireNonNull(now, "now").isBefore(recoveryDeadline(account));
    }
}
