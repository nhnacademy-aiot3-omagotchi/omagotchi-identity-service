package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** 관리자 권한으로 수행하는 계정 상태·로그인 잠금·전역 역할 변경. */
@Service
@RequiredArgsConstructor
public class AccountAdministrationService {

    private final AccountRepository accountRepository;
    private final SystemAdministratorReductionGuard administratorReductionGuard;
    private final Clock clock;

    @Transactional
    public Optional<Instant> disable(UUID actorAccountId, UUID targetAccountId) {
        List<Account> lockedAccounts = lockAccounts(actorAccountId, targetAccountId);
        Account actor = requireAuthorizedActor(lockedAccounts, actorAccountId);
        Account target = requireAccount(
                lockedAccounts, targetAccountId, AccountErrorCode.NOT_FOUND);

        if (target.getStatus() == AccountStatus.DISABLED) {
            return Optional.empty();
        }
        if (actor.getId().equals(target.getId())) {
            throw new BusinessException(AccountErrorCode.SELF_DISABLE_NOT_ALLOWED);
        }
        if (!target.isDisableAllowed()) {
            throw new BusinessException(AccountErrorCode.STATUS_TRANSITION_NOT_ALLOWED);
        }

        administratorReductionGuard.requireAnotherActiveAdministrator(target);
        target.disable(clock.instant());
        return Optional.of(target.getStatusChangedAt());
    }

    @Transactional
    public Optional<Instant> activate(UUID actorAccountId, UUID targetAccountId) {
        List<Account> lockedAccounts = lockAccounts(actorAccountId, targetAccountId);
        requireAuthorizedActor(lockedAccounts, actorAccountId);
        Account target = requireAccount(
                lockedAccounts, targetAccountId, AccountErrorCode.NOT_FOUND);
        if (!target.isActivationAllowed()) {
            throw new BusinessException(AccountErrorCode.STATUS_TRANSITION_NOT_ALLOWED);
        }

        return target.activate(clock.instant())
                ? Optional.of(target.getStatusChangedAt())
                : Optional.empty();
    }

    @Transactional
    public Optional<Instant> unlockLogin(UUID actorAccountId, UUID targetAccountId) {
        List<Account> lockedAccounts = lockAccounts(actorAccountId, targetAccountId);
        requireAuthorizedActor(lockedAccounts, actorAccountId);
        Account target = requireAccount(
                lockedAccounts, targetAccountId, AccountErrorCode.NOT_FOUND);
        Instant unlockedAt = clock.instant();
        return target.unlockLogin(unlockedAt)
                ? Optional.of(unlockedAt)
                : Optional.empty();
    }

    @Transactional
    public boolean grantSystemAdministrator(UUID actorAccountId, UUID targetAccountId) {
        return changeGlobalRole(actorAccountId, targetAccountId, GlobalRole.SYSTEM_ADMIN);
    }

    @Transactional
    public boolean revokeSystemAdministrator(UUID actorAccountId, UUID targetAccountId) {
        return changeGlobalRole(actorAccountId, targetAccountId, GlobalRole.USER);
    }

    private boolean changeGlobalRole(
            UUID actorAccountId,
            UUID targetAccountId,
            GlobalRole targetRole
    ) {
        List<Account> lockedAccounts = lockAccounts(actorAccountId, targetAccountId);
        Account actor = requireAuthorizedActor(lockedAccounts, actorAccountId);
        Account target = requireAccount(
                lockedAccounts, targetAccountId, AccountErrorCode.NOT_FOUND);

        if (target.getGlobalRole() == targetRole) {
            return false;
        }
        if (actor.getId().equals(target.getId())) {
            throw new BusinessException(AccountErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED);
        }
        if (!target.isGlobalRoleChangeAllowed()) {
            throw new BusinessException(AccountErrorCode.ROLE_CHANGE_NOT_ALLOWED);
        }
        if (targetRole == GlobalRole.USER) {
            administratorReductionGuard.requireAnotherActiveAdministrator(target);
        }

        target.changeGlobalRole(targetRole);
        return true;
    }

    private List<Account> lockAccounts(UUID actorAccountId, UUID targetAccountId) {
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
        if (!actor.isActiveSystemAdministrator()) {
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
}
