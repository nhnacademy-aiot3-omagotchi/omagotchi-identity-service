package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.exception.CommonErrorCode;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountQueryService {

    public static final int ACCOUNT_SEARCH_LIMIT = 20;
    public static final int ACCOUNT_SEARCH_QUERY_MAX_LENGTH = 100;

    private final AccountRepository accountRepository;

    public Account getById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.NOT_FOUND));
    }

    public List<Account> findAllByIds(Collection<UUID> accountIds) {
        return accountRepository.findAllById(accountIds.stream().distinct().toList());
    }

    public List<Account> searchByNameOrEmail(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank() || normalized.length() > ACCOUNT_SEARCH_QUERY_MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return accountRepository.searchByNameOrEmail(normalized, ACCOUNT_SEARCH_LIMIT);
    }
}
