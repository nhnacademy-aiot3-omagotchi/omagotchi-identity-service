package site.omagotchi.identityservice.accountaudit.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.accountaudit.application.port.AccountPermissionChangeAuditPage;
import site.omagotchi.identityservice.accountaudit.application.port.AccountPermissionChangeAuditRepository;
import site.omagotchi.identityservice.accountaudit.application.result.AccountPermissionAuditEntry;
import site.omagotchi.identityservice.accountaudit.application.result.AccountPermissionAuditPage;
import site.omagotchi.identityservice.accountaudit.application.result.AuditActor;
import site.omagotchi.identityservice.accountaudit.domain.AccountPermissionChangeAudit;
import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.exception.CommonErrorCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 전역 운영 관리자의 권한 변경 감사 조회 Use Case다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountAuditQueryService {

    public static final int PAGE_SIZE_DEFAULT = 20;
    public static final int PAGE_SIZE_MAX = 100;
    private static final int ACCOUNT_LOOKUP_BATCH_SIZE = 100;

    private final AccountPermissionChangeAuditRepository auditRepository;
    private final AccountRepository accountRepository;

    /** Bean Validation 을 우회한 호출까지 막기 위해 Application 경계에서 상한을 다시 검증한다. */
    public AccountPermissionAuditPage findRecent(int page, int size) {
        if (page < 0 || size < 1 || size > PAGE_SIZE_MAX) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        AccountPermissionChangeAuditPage found = auditRepository.findRecent(page, size);
        // 행마다 조회하지 않고 ID를 모으되, 계정 API의 요청당 최대 개수는 지킨다.
        Map<UUID, String> namesByUserId = resolveNames(found.content());

        List<AccountPermissionAuditEntry> entries = found.content().stream()
                .map(audit -> new AccountPermissionAuditEntry(
                        audit.getAuditType(),
                        audit.getAction(),
                        actor(audit.getActorUserId(), namesByUserId),
                        actor(audit.getTargetUserId(), namesByUserId),
                        audit.getBeforeValue(),
                        audit.getAfterValue(),
                        audit.getReason(),
                        audit.getOccurredAt()
                ))
                .toList();
        return new AccountPermissionAuditPage(entries, found.totalElements());
    }

    private Map<UUID, String> resolveNames(List<AccountPermissionChangeAudit> audits) {
        Set<UUID> userIds = audits.stream()
                .flatMap(audit -> Stream.of(audit.getActorUserId(), audit.getTargetUserId()))
                .collect(Collectors.toUnmodifiableSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> userIdList = List.copyOf(userIds);
        Map<UUID, String> namesByUserId = new HashMap<>();
        for (int fromIndex = 0; fromIndex < userIdList.size();
             fromIndex += ACCOUNT_LOOKUP_BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + ACCOUNT_LOOKUP_BATCH_SIZE, userIdList.size());
            accountRepository.findAllById(userIdList.subList(fromIndex, toIndex))
                    .forEach(account -> namesByUserId.putIfAbsent(
                            account.getId(), account.getName()
                    ));
        }
        return Map.copyOf(namesByUserId);
    }

    // FK 가 계정 존재를 보장하지만, 조회에 실패해도 감사 한 줄을 통째로 잃지는 않는다.
    private static AuditActor actor(UUID userId, Map<UUID, String> namesByUserId) {
        String name = namesByUserId.get(userId);
        return name == null ? AuditActor.unknown(userId) : new AuditActor(userId, name);
    }
}
