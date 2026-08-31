package site.omagotchi.identityservice.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Objects;
import java.util.UUID;

/**
 * 관리자 API 호출자의 권한을 요청 시점의 DB 상태로 재확인한다.
 *
 * <p>Access JWT의 {@code role} Claim은 발급 이후 최대 Token 수명만큼 낡을 수 있다.
 * 권한 회수·계정 정지 직후에도 이전 Token으로 관리자 API가 열리는 창을 닫기 위한 경계다.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminAccessGuard {

    private final AccountRepository accountRepository;

    public UUID requireSystemAdmin(UUID accountId) {
        UUID actorId = Objects.requireNonNull(accountId, "accountId");

        /*
         * 계정 소멸·강등·정지를 모두 403으로 수렴시킨다.
         * 404로 구분하면 관리자 API가 계정 존재 여부 확인 수단이 된다.
         */
        Account actor = accountRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(
                        AccountErrorCode.ADMIN_ACCESS_NOT_ALLOWED));

        if (actor.getGlobalRole() != GlobalRole.SYSTEM_ADMIN
                || actor.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(AccountErrorCode.ADMIN_ACCESS_NOT_ALLOWED);
        }
        return actorId;
    }
}
