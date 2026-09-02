package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.application.result.AccountRoleChangeResult;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.account.application.result.AccountStateChangeResult;
import site.omagotchi.identityservice.account.application.result.AccountStateValue;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.AccountStatusTransition;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

    private final AccountRepository accountRepository;
    private final SystemAdministratorReductionGuard administratorReductionGuard;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountStateChangeResult withdraw(
            UUID accountId,
            String currentRawPassword
    ) {
        Account account = accountRepository.lockById(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.NOT_FOUND));

        // 이미 탈퇴한 계정의 멱등 처리
        if (account.getStatus() == AccountStatus.WITHDRAWN) {
            return result(
                    accountId,
                    AccountStatusTransition.unchanged(AccountStatus.WITHDRAWN)
            );
        }
        if (!account.isWithdrawalAllowed()) {
            throw new BusinessException(AccountErrorCode.WITHDRAWAL_NOT_ALLOWED);
        }
        if (!passwordHasher.matches(
                currentRawPassword == null ? "" : currentRawPassword,
                account.getPasswordHash()
        )) {
            throw new BusinessException(AccountErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        // 계정 행 잠금 뒤 마지막 이용 가능 SYSTEM_ADMIN 보존 검증
        administratorReductionGuard.requireAnotherUsableAdministrator(account);
        return result(
                accountId,
                account.withdraw(clock.instant())
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountStateChangeResult disableByAdministrator(
            UUID actorAccountId,
            UUID targetAccountId
    ) {
        List<Account> lockedAccounts = lockAccounts(actorAccountId, targetAccountId);
        // JWT의 역할 정보가 아닌 DB에서 잠근 계정의 역할·상태 기준 권한 재확인
        Account actor = requireAuthorizedActor(lockedAccounts, actorAccountId);
        Account target = requireAccount(
                lockedAccounts,
                targetAccountId,
                AccountErrorCode.NOT_FOUND
        );

        // 후속 처리 생략을 위한 변경 없음 결과 반환
        if (target.getStatus() == AccountStatus.DISABLED) {
            return result(
                    targetAccountId,
                    AccountStatusTransition.unchanged(AccountStatus.DISABLED)
            );
        }
        // 관리자 자신의 계정 비활성화 방지
        if (actor.getId().equals(target.getId())) {
            throw new BusinessException(AccountErrorCode.SELF_DISABLE_NOT_ALLOWED);
        }
        if (!target.isDisableAllowed()) {
            throw new BusinessException(AccountErrorCode.STATUS_TRANSITION_NOT_ALLOWED);
        }

        // 계정 행 잠금 뒤 마지막 이용 가능 SYSTEM_ADMIN 보존 검증
        administratorReductionGuard.requireAnotherUsableAdministrator(target);
        return result(
                targetAccountId,
                target.disable()
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountStateChangeResult activateByAdministrator(
            UUID actorAccountId,
            UUID targetAccountId
    ) {
        List<Account> lockedAccounts = lockAccounts(actorAccountId, targetAccountId);
        // JWT의 역할 정보가 아닌 DB에서 잠근 계정의 역할·상태 기준 권한 재확인
        requireAuthorizedActor(lockedAccounts, actorAccountId);
        Account target = requireAccount(
                lockedAccounts,
                targetAccountId,
                AccountErrorCode.NOT_FOUND
        );
        if (!target.isActivationAllowed()) {
            throw new BusinessException(AccountErrorCode.STATUS_TRANSITION_NOT_ALLOWED);
        }
        // 이용 가능한 관리자 수를 줄이지 않는 전이의 보호 행 잠금 제외
        return result(
                targetAccountId,
                target.activate()
        );
    }

    /**
     * 관리자가 다른 계정의 전역 역할을 바꾼다.
     *
     * <p>강등은 이용 가능한 SYSTEM_ADMIN 수를 줄이므로
     * {@link SystemAdministratorReductionGuard}로 보호 행을 잠근 뒤 재조회한다.
     * 잠금 뒤에 세기 때문에 두 관리자가 서로를 동시에 강등해도 뒤 요청이 막힌다.</p>
     *
     * <p>자기 자신은 바꿀 수 없다. 마지막 관리자 검사만으로는 관리자가 2명일 때
     * 스스로 강등해 운영에서 빠지는 실수를 막지 못한다.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AccountRoleChangeResult changeGlobalRoleByAdministrator(
            UUID actorAccountId,
            UUID targetAccountId,
            GlobalRole newGlobalRole
    ) {
        List<Account> lockedAccounts = lockAccounts(actorAccountId, targetAccountId);
        // JWT의 역할 정보가 아닌 DB에서 잠근 계정의 역할·상태 기준 권한 재확인
        Account actor = requireAuthorizedActor(lockedAccounts, actorAccountId);
        Account target = requireAccount(
                lockedAccounts,
                targetAccountId,
                AccountErrorCode.NOT_FOUND
        );

        GlobalRole before = target.getGlobalRole();
        // 후속 처리 생략을 위한 변경 없음 결과 반환
        if (before == newGlobalRole) {
            return new AccountRoleChangeResult(targetAccountId, before, before);
        }
        // 관리자 자신의 역할 변경 방지
        if (actor.getId().equals(target.getId())) {
            throw new BusinessException(AccountErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED);
        }
        if (!target.isGlobalRoleChangeAllowed()) {
            throw new BusinessException(AccountErrorCode.ROLE_CHANGE_NOT_ALLOWED);
        }

        // 계정 행 잠금 뒤 마지막 이용 가능 SYSTEM_ADMIN 보존 검증
        if (newGlobalRole == GlobalRole.USER) {
            administratorReductionGuard.requireAnotherUsableAdministrator(target);
        }

        target.changeGlobalRole(newGlobalRole);
        return new AccountRoleChangeResult(targetAccountId, before, newGlobalRole);
    }

    private List<Account> lockAccounts(UUID actorAccountId, UUID targetAccountId) {
        // 교차 요청의 데드락 방지를 위한 UUID 오름차순 잠금
        return accountRepository.lockAllByIdInOrder(
                Stream.of(actorAccountId, targetAccountId).distinct().toList()
        );
    }

    private Account requireAuthorizedActor(
            Collection<Account> lockedAccounts,
            UUID actorAccountId
    ) {
        Account actor = requireAccount(
                lockedAccounts,
                actorAccountId,
                AccountErrorCode.ADMIN_OPERATION_NOT_ALLOWED
        );
        if (!actor.isUsableSystemAdministrator()) {
            throw new BusinessException(AccountErrorCode.ADMIN_OPERATION_NOT_ALLOWED);
        }
        return actor;
    }

    private Account requireAccount(
            Collection<Account> lockedAccounts,
            UUID accountId,
            AccountErrorCode errorCode
    ) {
        return lockedAccounts.stream()
                .filter(account -> account.getId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(errorCode));
    }

    private AccountStateChangeResult result(
            UUID targetAccountId,
            AccountStatusTransition transition
    ) {
        return new AccountStateChangeResult(
                targetAccountId,
                toStateValue(transition.before()),
                toStateValue(transition.after())
        );
    }

    private AccountStateValue toStateValue(AccountStatus status) {
        return switch (status) {
            case ACTIVE -> AccountStateValue.ACTIVE;
            case LOCKED -> AccountStateValue.LOCKED;
            case DISABLED -> AccountStateValue.DISABLED;
            case WITHDRAWN -> AccountStateValue.WITHDRAWN;
        };
    }
}
