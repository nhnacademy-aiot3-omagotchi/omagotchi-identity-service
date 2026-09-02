package site.omagotchi.identityservice.account.application.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.domain.GlobalRole;

import java.util.UUID;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class AccountRoleChangeResultTest {

    private static final UUID TARGET_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );

    @Test
    @DisplayName("공개 결과가 전역 역할 전이 의미를 제공")
    void describesGlobalRoleTransition() {
        // Given
        AccountRoleChangeResult granted = result(GlobalRole.USER, GlobalRole.SYSTEM_ADMIN);
        AccountRoleChangeResult revoked = result(GlobalRole.SYSTEM_ADMIN, GlobalRole.USER);
        AccountRoleChangeResult unchanged = result(GlobalRole.USER, GlobalRole.USER);

        // When & Then
        thenSoftly(softly -> {
            softly.then(granted.changed()).isTrue();
            softly.then(granted.granted()).isTrue();
            softly.then(granted.revoked()).isFalse();
            softly.then(revoked.changed()).isTrue();
            softly.then(revoked.revoked()).isTrue();
            softly.then(revoked.granted()).isFalse();
            softly.then(unchanged.changed()).isFalse();
            softly.then(unchanged.granted()).isFalse();
            softly.then(unchanged.revoked()).isFalse();
        });
    }

    private AccountRoleChangeResult result(GlobalRole before, GlobalRole after) {
        return new AccountRoleChangeResult(TARGET_ID, before, after);
    }
}
