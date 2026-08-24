package site.omagotchi.identityservice.account.infrastructure;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountJpaPersistence implements AccountRepository {

    private static final String EMAIL_CONSTRAINT = "uq_accounts_email";

    private final AccountJpaRepository accountJpaRepository;

    @Override
    public Optional<Account> findById(UUID accountId) {
        return accountJpaRepository.findById(accountId);
    }

    @Override
    public List<Account> findAllById(Collection<UUID> accountIds) {
        return accountJpaRepository.findAllById(accountIds);
    }

    @Override
    public Optional<Account> findByEmail(String email) {
        return accountJpaRepository.findByEmail(email);
    }

    @Override
    public Optional<Account> lockByEmail(String email) {
        return accountJpaRepository.lockByEmail(email);
    }

    @Override
    public Account create(Account account) {
        try {
            // UNIQUE 제약 위반의 현재 Persistence 경계 내 판별을 위한 즉시 반영
            return accountJpaRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailConstraintViolation(exception)) {
                throw new BusinessException(AccountErrorCode.DUPLICATE_EMAIL, exception);
            }
            throw exception;
        }
    }

    // Spring·Hibernate 예외 래퍼 내부의 실제 DB 제약 이름 탐색
    private boolean isEmailConstraintViolation(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                return EMAIL_CONSTRAINT.equals(constraintViolation.getConstraintName());
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }
}
