package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AdminAccessGuardTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AdminAccessGuard adminAccessGuard = new AdminAccessGuard(accountRepository);

    @Test
    @DisplayName("활성 상태의 SYSTEM_ADMIN 통과")
    void allowsActiveSystemAdmin() {
        // Given
        UUID accountId = UUID.randomUUID();
        // Account mock의 스텁을 먼저 끝낸 뒤 Repository 스텁을 시작한다.
        Account actor = account(GlobalRole.SYSTEM_ADMIN, AccountStatus.ACTIVE);
        given(accountRepository.findById(accountId)).willReturn(Optional.of(actor));

        // When
        UUID actorId = adminAccessGuard.requireSystemAdmin(accountId);

        // Then
        then(actorId).isEqualTo(accountId);
    }

    @Test
    @DisplayName("Token 발급 이후 USER로 강등된 계정 거부")
    void rejectsDemotedAccount() {
        // Given
        UUID accountId = UUID.randomUUID();
        Account actor = account(GlobalRole.USER, AccountStatus.ACTIVE);
        given(accountRepository.findById(accountId)).willReturn(Optional.of(actor));

        // When
        // Then
        thenThrownBy(() -> adminAccessGuard.requireSystemAdmin(accountId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AccountErrorCode.ADMIN_ACCESS_NOT_ALLOWED);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("비활성 상태의 SYSTEM_ADMIN 거부")
    void rejectsNonActiveSystemAdmin(AccountStatus status) {
        // Given
        UUID accountId = UUID.randomUUID();
        Account actor = account(GlobalRole.SYSTEM_ADMIN, status);
        given(accountRepository.findById(accountId)).willReturn(Optional.of(actor));

        // When
        // Then
        thenThrownBy(() -> adminAccessGuard.requireSystemAdmin(accountId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("소멸된 계정은 404가 아닌 관리자 접근 거부로 수렴")
    void rejectsMissingAccountAsForbidden() {
        // Given
        UUID accountId = UUID.randomUUID();
        given(accountRepository.findById(accountId)).willReturn(Optional.empty());

        // When
        // Then
        thenThrownBy(() -> adminAccessGuard.requireSystemAdmin(accountId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AccountErrorCode.ADMIN_ACCESS_NOT_ALLOWED);
    }

    @Test
    @DisplayName("null 식별자 방어")
    void rejectsNullAccountId() {
        // When
        // Then
        thenThrownBy(() -> adminAccessGuard.requireSystemAdmin(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static Account account(GlobalRole role, AccountStatus status) {
        Account account = mock(Account.class);
        given(account.getGlobalRole()).willReturn(role);
        given(account.getStatus()).willReturn(status);
        return account;
    }
}
