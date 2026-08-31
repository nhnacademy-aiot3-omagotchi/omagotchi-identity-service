package site.omagotchi.identityservice.account.application.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class AccountStateChangeResultTest {

    private static final UUID TARGET_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );

    @Test
    @DisplayName("공개 결과가 Account 상태 전이 의미를 제공")
    void describesAccountStateTransition() {
        // Given
        AccountStateChangeResult disabled = result(
                AccountStateValue.LOCKED,
                AccountStateValue.DISABLED
        );
        AccountStateChangeResult unlocked = result(
                AccountStateValue.LOCKED,
                AccountStateValue.ACTIVE
        );
        AccountStateChangeResult reactivated = result(
                AccountStateValue.DISABLED,
                AccountStateValue.ACTIVE
        );
        AccountStateChangeResult unchanged = result(
                AccountStateValue.ACTIVE,
                AccountStateValue.ACTIVE
        );

        // When
        boolean disabledTransition = disabled.disabled();
        boolean unlockedTransition = unlocked.unlocked();
        boolean reactivatedTransition = reactivated.reactivated();

        // Then
        thenSoftly(softly -> {
            softly.then(disabledTransition).isTrue();
            softly.then(unlockedTransition).isTrue();
            softly.then(reactivatedTransition).isTrue();
            softly.then(unchanged.changed()).isFalse();
            softly.then(unchanged.disabled()).isFalse();
            softly.then(unchanged.unlocked()).isFalse();
            softly.then(unchanged.reactivated()).isFalse();
        });
    }

    private AccountStateChangeResult result(
            AccountStateValue before,
            AccountStateValue after
    ) {
        return new AccountStateChangeResult(TARGET_ID, before, after);
    }
}
