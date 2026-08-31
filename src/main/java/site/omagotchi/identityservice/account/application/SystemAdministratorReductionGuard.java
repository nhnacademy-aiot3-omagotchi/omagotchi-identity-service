package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;

@Component
@RequiredArgsConstructor
class SystemAdministratorReductionGuard {

    private final AccountRepository accountRepository;

    void requireAnotherUsableAdministrator(Account target) {
        // 일반 사용자와 이미 이용 불가능한 관리자의 불필요한 직렬화 제외
        if (!target.isUsableSystemAdministrator()) {
            return;
        }

        // 관리자 감소 요청만 직렬화하는 단일 보호 행 잠금
        accountRepository.lockSystemAdministratorGuard();
        // 보호 행 대기 후 선행 요청 커밋을 반영한 관리자 수 재조회
        if (accountRepository.countUsableSystemAdministrators() <= 1) {
            throw new BusinessException(AccountErrorCode.LAST_SYSTEM_ADMIN);
        }
    }
}
