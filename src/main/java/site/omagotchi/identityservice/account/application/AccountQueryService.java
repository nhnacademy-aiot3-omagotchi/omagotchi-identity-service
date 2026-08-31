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
    public static final int ACCOUNT_SEARCH_CANDIDATE_IDS_MAX = 100;

    private final AccountRepository accountRepository;

    public Account getById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.NOT_FOUND));
    }

    public List<Account> findAllByIds(Collection<UUID> accountIds) {
        return accountRepository.findAllById(accountIds.stream().distinct().toList());
    }

    public List<Account> searchByNameOrEmail(String query, Collection<UUID> candidateIds) {
        String normalized = query == null ? "" : query.strip();
        if (normalized.isBlank() || normalized.length() > ACCOUNT_SEARCH_QUERY_MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        List<UUID> distinctCandidateIds = candidateIds == null ? List.of()
                : candidateIds.stream().distinct().toList();
        if (distinctCandidateIds.isEmpty() || distinctCandidateIds.stream().anyMatch(id -> id == null)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        if (distinctCandidateIds.size() > ACCOUNT_SEARCH_CANDIDATE_IDS_MAX) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return accountRepository.searchByNameOrEmail(
                escapeLikePattern(normalized), distinctCandidateIds, ACCOUNT_SEARCH_LIMIT);
    }

    private static String escapeLikePattern(String query) {
        return query.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
