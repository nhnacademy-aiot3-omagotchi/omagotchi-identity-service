package site.omagotchi.identityservice.account.infrastructure;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountErrorCode;
import site.omagotchi.identityservice.global.exception.BusinessException;

/**
 * 두 요청이 같은 이메일로 중복 검사를 통과한 경우 한 요청의 실패를 비지니스 예외로 정확하게 알려주기 위함
 * - 해당 경우 한 요청은 INSERT를 성공하지만
 * - 다른 요청은 INSERT가 UNIQUE 제약을 위반해 실패하게 됨
 * - 해당 실패를 도메인 비지니스 예외로 변환함
 * - DB의 UNIQUE 제약 조건을 직접 명시하는 건 다른 대안들에 비해 감수할만 하다고 판단함
 */
@Component
@RequiredArgsConstructor
public class AccountStore {

    private static final String EMAIL_CONSTRAINT = "uq_accounts_email";

    private final AccountJpaRepository accountJpaRepository;

    public Account save(Account account) {
        try {
            return accountJpaRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        if (EMAIL_CONSTRAINT.equals(findConstraintName(exception))) {
            return new BusinessException(AccountErrorCode.DUPLICATE_EMAIL);
        }
        return exception;
    }

    private String findConstraintName(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }
}
