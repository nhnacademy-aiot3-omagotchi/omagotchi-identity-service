package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountProfileService {

    private final AccountRepository accountRepository;

    @Transactional
    public void changeName(UUID accountId, String newName) {
        if (!Account.isNameValid(newName)) {
            throw new BusinessException(AccountErrorCode.INVALID_NAME);
        }

        // 동일한 accounts 행의 인증 상태 변경과 직렬화를 통한 갱신 유실 방지
        Account account = accountRepository.lockById(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.NOT_FOUND));

        if (!account.isManagementAllowed()) {
            throw new BusinessException(AccountErrorCode.NAME_CHANGE_NOT_ALLOWED);
        }
        account.changeName(newName);
    }
}
