package site.omagotchi.identityservice.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

@DisplayName("AccountStatus 도메인 단위 테스트")
class AccountStatusTest {

    @Test
    @DisplayName("로그인 잠금은 계정 생명주기 상태에 포함하지 않는다")
    void excludesLoginLockFromLifecycleStatuses() {
        then(AccountStatus.values()).containsExactly(
                AccountStatus.ACTIVE,
                AccountStatus.DISABLED,
                AccountStatus.WITHDRAWN
        );
    }
}
