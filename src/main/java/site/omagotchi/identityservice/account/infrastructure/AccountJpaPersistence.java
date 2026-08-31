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
    public Optional<Account> lockById(UUID accountId) {
        return accountJpaRepository.lockById(accountId);
    }

    @Override
    public List<Account> lockAllByIdInOrder(Collection<UUID> accountIds) {
        return accountJpaRepository.lockAllByIdInOrder(accountIds);
    }

    @Override
    public void lockSystemAdministratorGuard() {
        Integer lockedGuardId = accountJpaRepository.lockSystemAdministratorGuard();
        // 마이그레이션 누락이나 보호 행 손상에 대한 즉시 실패
        if (!Integer.valueOf(1).equals(lockedGuardId)) {
            throw new IllegalStateException("SYSTEM_ADMIN 보호 행을 찾을 수 없습니다.");
        }
    }

    @Override
    public long countUsableSystemAdministrators() {
        return accountJpaRepository.countUsableSystemAdministrators();
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
            // 이메일 중복 제약을 즉시 확인하기 위한 DB 반영
            return accountJpaRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailConstraintViolation(exception)) {
                throw new BusinessException(AccountErrorCode.DUPLICATE_EMAIL, exception);
            }
            throw exception;
        }
    }

    // 중첩된 Spring·Hibernate 예외에서 실제 DB 제약 이름 확인
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
