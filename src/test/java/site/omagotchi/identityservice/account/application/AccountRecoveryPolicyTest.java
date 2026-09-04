package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.domain.Account;

import java.time.Instant;

import static org.assertj.core.api.BDDAssertions.then;

class AccountRecoveryPolicyTest {

    private static final Instant POLICY_EFFECTIVE_AT =
            Instant.parse("2026-09-03T00:00:00Z");

    private final AccountRecoveryPolicy policy = new AccountRecoveryPolicy(
            new AccountRecoveryProperties(POLICY_EFFECTIVE_AT)
    );

    @Test
    @DisplayName("정책 도입 전 탈퇴 계정은 정책 시행일부터 30일 유예")
    void grantsLegacyWithdrawalGraceFromPolicyEffectiveAt() {
        Account account = withdrawnAt(Instant.parse("2026-08-01T00:00:00Z"));

        then(policy.recoveryDeadline(account))
                .isEqualTo(Instant.parse("2026-10-03T00:00:00Z"));
    }

    @Test
    @DisplayName("정책 도입 후 탈퇴 계정은 상태 변경 시각부터 30일 복구")
    void calculatesDeadlineFromWithdrawalTime() {
        Account account = withdrawnAt(Instant.parse("2026-09-10T00:00:00Z"));

        then(policy.recoveryDeadline(account))
                .isEqualTo(Instant.parse("2026-10-10T00:00:00Z"));
    }

    @Test
    @DisplayName("복구 마감 시각부터 복구 불가")
    void excludesExactDeadline() {
        Account account = withdrawnAt(Instant.parse("2026-09-10T00:00:00Z"));
        Instant deadline = policy.recoveryDeadline(account);

        then(policy.canRecover(account, deadline.minusNanos(1))).isTrue();
        then(policy.canRecover(account, deadline)).isFalse();
    }

    private Account withdrawnAt(Instant withdrawnAt) {
        Account account = Account.register(
                "member@example.com",
                "password-hash",
                "member",
                Instant.parse("2026-07-01T00:00:00Z")
        );
        account.withdraw(withdrawnAt);
        return account;
    }
}
