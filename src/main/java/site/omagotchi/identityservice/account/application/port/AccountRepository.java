package site.omagotchi.identityservice.account.application.port;

import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Optional<Account> findById(UUID accountId);

    Optional<Account> findByEmail(String email);

    /**
     * @throws BusinessException 이미 저장된 이메일과 충돌해 {@code DUPLICATE_EMAIL}이 발생한 경우
     */
    Account create(Account account);
}
