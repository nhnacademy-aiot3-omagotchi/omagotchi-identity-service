package site.omagotchi.identityservice.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.BDDAssertions.then;

@DisplayName("AccountStatus 도메인 단위 테스트")
class AccountStatusTest {

    @Test
    @DisplayName("로그인은 ACTIVE 상태에서만 허용된다")
    void loginAllowedOnlyInActive() {
        then(AccountStatus.ACTIVE.isLoginAllowed()).isTrue();
        then(AccountStatus.LOCKED.isLoginAllowed()).isFalse();
        then(AccountStatus.DISABLED.isLoginAllowed()).isFalse();
        then(AccountStatus.WITHDRAWN.isLoginAllowed()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "LOCKED"})
    @DisplayName("일반 계정 관리는 ACTIVE와 LOCKED 상태에서 허용된다")
    void managementAllowedInActiveAndLocked(AccountStatus status) {
        then(status.isManagementAllowed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"DISABLED", "WITHDRAWN"})
    @DisplayName("비활성 및 탈퇴 계정은 일반 계정 관리가 허용되지 않는다")
    void managementNotAllowedInDisabledAndWithdrawn(AccountStatus status) {
        then(status.isManagementAllowed()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "LOCKED", "DISABLED"})
    @DisplayName("활성화는 탈퇴를 제외한 ACTIVE, LOCKED, DISABLED 상태에서 허용된다")
    void activationAllowedInNonWithdrawnStatuses(AccountStatus status) {
        then(status.isActivationAllowed()).isTrue();
    }

    @Test
    @DisplayName("탈퇴 계정은 활성화가 허용되지 않는다")
    void activationNotAllowedInWithdrawn() {
        then(AccountStatus.WITHDRAWN.isActivationAllowed()).isFalse();
    }
}
