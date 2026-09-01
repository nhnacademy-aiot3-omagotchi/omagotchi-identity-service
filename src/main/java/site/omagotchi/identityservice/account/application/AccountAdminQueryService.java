package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountPage;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.AccountSearchCriteria;
import site.omagotchi.identityservice.account.application.port.AccountSortOption;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.exception.CommonErrorCode;

/** 전역 운영 관리자의 계정 목록 조회 Use Case다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountAdminQueryService {

    public static final int PAGE_SIZE_DEFAULT = 20;
    public static final int PAGE_SIZE_MAX = 100;
    public static final int KEYWORD_MAX_LENGTH = 100;

    private final AccountRepository accountRepository;

    /**
     * Bean Validation을 우회한 호출까지 막기 위해 Application 경계에서 상한을 다시 검증한다.
     *
     * @param keyword null이면 검색어 조건을 적용하지 않는다.
     */
    public AccountPage search(
            String keyword,
            AccountStatus status,
            GlobalRole role,
            int page,
            int size,
            AccountSortOption sortOption
    ) {
        if (page < 0 || size < 1 || size > PAGE_SIZE_MAX) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        AccountSortOption resolvedSortOption =
                sortOption == null ? AccountSortOption.CREATED_AT_DESC : sortOption;

        return accountRepository.searchAccounts(
                new AccountSearchCriteria(normalizeKeyword(keyword), status, role),
                page,
                size,
                resolvedSortOption
        );
    }

    /*
     * 공백만 입력한 검색어를 "조건 없음"으로 승격하면 사용자가 전체 목록을 검색 결과로 오인한다.
     * 검색어를 보냈다면 유효한 검색어여야 한다.
     */
    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String normalized = keyword.strip();
        if (normalized.isEmpty() || normalized.length() > KEYWORD_MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return normalized;
    }
}
