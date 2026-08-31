package site.omagotchi.identityservice.account.application.port;

import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;

/**
 * 관리자 사용자 목록의 조회 조건이다.
 *
 * <p>null 필드는 "해당 조건 미적용"을 의미하며, {@code keyword}는 Application 계층에서
 * 정규화·길이 검증을 통과한 값만 전달된다.
 */
public record AccountSearchCriteria(
        String keyword,
        AccountStatus status,
        GlobalRole role
) {
}
