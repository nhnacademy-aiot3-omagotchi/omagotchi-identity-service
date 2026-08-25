package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.result.AccountSessionStateResult;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountSessionStateService {

    private final AccountRepository accountRepository;

    @Transactional
    public AccountSessionStateResult lockById(UUID accountId) {
        return accountRepository.lockById(accountId)
                .map(AccountSessionStateResult::from)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.NOT_FOUND));
    }
}
