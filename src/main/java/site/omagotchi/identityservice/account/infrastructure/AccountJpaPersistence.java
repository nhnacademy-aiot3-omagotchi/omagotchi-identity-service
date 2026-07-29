package site.omagotchi.identityservice.account.infrastructure;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;

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
    public Optional<Account> findByEmail(String email) {
        return accountJpaRepository.findByEmail(email);
    }

    @Override
    public Account create(Account account) {
        try {
            // UNIQUE 제약 위반을 이 메서드 안에서 판별하기 위해 즉시 반영
            return accountJpaRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailConstraintViolation(exception)) {
                throw new BusinessException(AccountErrorCode.DUPLICATE_EMAIL, exception);
            }
            throw exception;
        }
    }

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
