package site.omagotchi.identityservice.account.infrastructure;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.port.AccountPage;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.AccountSearchCriteria;
import site.omagotchi.identityservice.account.application.port.AccountSortOption;
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

    // 정렬값이 같은 행의 페이지 경계 중복·누락을 막는 최종 Tie-breaker
    private static final Sort.Order ID_TIE_BREAKER = Sort.Order.asc("id");

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
    public List<Account> searchByNameOrEmail(String query, Collection<UUID> candidateIds, int limit) {
        return accountJpaRepository.searchByNameOrEmail(
                query, candidateIds, PageRequest.of(0, limit));
    }

    @Override
    public AccountPage searchAccounts(
            AccountSearchCriteria criteria,
            int page,
            int size,
            AccountSortOption sortOption
    ) {
        Page<Account> found = accountJpaRepository.findAll(
                AccountSpecifications.of(criteria),
                PageRequest.of(page, size, toSort(sortOption))
        );
        return new AccountPage(found.getContent(), found.getTotalElements());
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

    // Entity 필드명 노출 없이 허용된 정렬만 생성
    private static Sort toSort(AccountSortOption sortOption) {
        Sort.Order primary = switch (sortOption) {
            case CREATED_AT_DESC -> Sort.Order.desc("createdAt");
            case CREATED_AT_ASC -> Sort.Order.asc("createdAt");
            case EMAIL_ASC -> Sort.Order.asc("email");
            case NAME_ASC -> Sort.Order.asc("name");
        };
        return Sort.by(primary, ID_TIE_BREAKER);
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
