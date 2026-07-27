package site.omagotchi.identityservice.account.infrastructure;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountErrorCode;
import site.omagotchi.identityservice.global.exception.BusinessException;

@Component
@RequiredArgsConstructor
public class AccountStore {

    private static final String EMAIL_CONSTRAINT = "uq_accounts_email";

    private final AccountJpaRepository accountJpaRepository;

    public Account save(Account account) {
        try {
            // UNIQUE 제약 위반을 이 메서드 안에서 변환하기 위해 즉시 반영
            return accountJpaRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraintViolation(exception);
        }
    }

    private RuntimeException translateConstraintViolation(DataIntegrityViolationException exception) {
        if (EMAIL_CONSTRAINT.equals(extractConstraintName(exception))) {
            return new BusinessException(AccountErrorCode.DUPLICATE_EMAIL);
        }
        return exception;
    }

    private String extractConstraintName(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                // 해당 예외가 제약조건 위반으로 인한 예외라면
                return constraintViolation.getConstraintName();
            }
            if (current.getCause() == current) {
                break; // 비정상적 자기참조 예외로 인한 무한 반복 방어 (스프링, Hibernate에서는 필요 없다고는 함)
            }
            current = current.getCause();
        }
        return null;
    }
}
