package site.omagotchi.identityservice.account.application.port;

import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Optional<Account> findById(UUID accountId);

    Optional<Account> lockById(UUID accountId);

    List<Account> lockAllByIdInOrder(Collection<UUID> accountIds);

    void lockSystemAdministratorGuard();

    long countUsableSystemAdministrators();

    List<Account> findAllById(Collection<UUID> accountIds);

    Optional<Account> findByEmail(String email);

    Optional<Account> lockByEmail(String email);

    /**
     * @throws BusinessException 이미 저장된 이메일과 충돌해 {@code DUPLICATE_EMAIL}이 발생한 경우
     */
    Account create(Account account);
}
