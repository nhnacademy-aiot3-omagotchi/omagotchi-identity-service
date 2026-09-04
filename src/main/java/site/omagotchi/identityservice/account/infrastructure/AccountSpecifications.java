package site.omagotchi.identityservice.account.infrastructure;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import site.omagotchi.identityservice.account.application.port.AccountSearchCriteria;
import site.omagotchi.identityservice.account.domain.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 관리자 사용자 목록의 선택적 조건을 조립한다.
 *
 * <p>JPQL의 {@code :param IS NULL} 분기 대신 Specification을 사용해
 * 미적용 조건이 실행 계획에 남지 않도록 한다.
 */
final class AccountSpecifications {

    // JPQL·Criteria 양쪽에서 동일하게 사용하는 LIKE Escape 문자
    private static final char ESCAPE_CHARACTER = '!';

    private AccountSpecifications() {
    }

    static Specification<Account> of(AccountSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.role() != null) {
                predicates.add(builder.equal(root.get("globalRole"), criteria.role()));
            }
            if (Boolean.TRUE.equals(criteria.locked())) {
                predicates.add(builder.greaterThan(root.get("lockedUntil"), criteria.checkedAt()));
            } else if (Boolean.FALSE.equals(criteria.locked())) {
                predicates.add(builder.or(
                        builder.isNull(root.get("lockedUntil")),
                        builder.lessThanOrEqualTo(root.get("lockedUntil"), criteria.checkedAt())
                ));
            }
            if (criteria.keyword() != null) {
                // email은 정규화된 소문자로 저장되므로 양쪽 모두 소문자 기준으로 비교한다.
                String pattern = "%"
                        + escapeLikePattern(criteria.keyword().toLowerCase(Locale.ROOT))
                        + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, ESCAPE_CHARACTER),
                        builder.like(root.get("email"), pattern, ESCAPE_CHARACTER)
                ));
            }

            // 조건이 없는 전체 조회도 유효한 관리자 유스케이스
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    // 사용자 입력의 LIKE 메타문자를 리터럴로 고정해 의도치 않은 전체 매칭을 막는다.
    private static String escapeLikePattern(String query) {
        return query.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
