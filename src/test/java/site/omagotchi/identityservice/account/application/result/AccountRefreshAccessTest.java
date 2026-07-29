package site.omagotchi.identityservice.account.application.result;

import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.domain.AccountStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AccountRefreshAccessTest {

    @Test
    void convertsAccountStatusToRefreshAccess() {
        assertThat(AccountRefreshAccess.from(AccountStatus.ACTIVE))
                .isEqualTo(AccountRefreshAccess.ALLOWED);
        assertThat(AccountRefreshAccess.from(AccountStatus.LOCKED))
                .isEqualTo(AccountRefreshAccess.ALLOWED);
        assertThat(AccountRefreshAccess.from(AccountStatus.DISABLED))
                .isEqualTo(AccountRefreshAccess.ACCOUNT_DISABLED);
        assertThat(AccountRefreshAccess.from(AccountStatus.WITHDRAWN))
                .isEqualTo(AccountRefreshAccess.ACCOUNT_WITHDRAWN);
    }
}
