package site.omagotchi.identityservice.accountaudit.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.AdminAccessGuard;
import site.omagotchi.identityservice.accountaudit.application.AccountAuditQueryService;
import site.omagotchi.identityservice.accountaudit.application.result.AccountPermissionAuditPage;
import site.omagotchi.identityservice.accountaudit.presentation.request.AdminAuditSearchRequest;
import site.omagotchi.identityservice.accountaudit.presentation.response.AdminAuditPageResponse;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Objects;
import java.util.UUID;

/** 전역 운영 관리자의 권한 변경 감사 조회 경계. */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/audits")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AccountAuditQueryService accountAuditQueryService;
    private final AdminAccessGuard adminAccessGuard;

    @GetMapping
    public ResponseEntity<AdminAuditPageResponse> getAudits(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute AdminAuditSearchRequest request
    ) {
        // Filter Chain의 role Claim 검사 이후 요청 시점 DB 권한으로 최종 확인
        UUID actorId = adminAccessGuard.requireSystemAdmin(actorIdOf(jwt));

        int page = request.pageOrDefault();
        int size = request.sizeOrDefault();
        AccountPermissionAuditPage auditPage = accountAuditQueryService.findRecent(page, size);

        // 감사 기록 열람 자체도 감사 대상이 되는 행위이므로 호출 사실을 남긴다.
        log.info(
                "관리자 감사 로그 조회 actorId={}, page={}, size={}, total={}",
                actorId,
                page,
                size,
                auditPage.totalElements()
        );

        return ResponseEntity.ok()
                // 권한 이력이 브라우저·중간 캐시에 남지 않게 한다.
                .cacheControl(CacheControl.noStore())
                .body(AdminAuditPageResponse.of(auditPage, page, size));
    }

    // sub 형식 오류를 500이 아닌 관리자 접근 거부로 수렴시킨다.
    private static UUID actorIdOf(Jwt jwt) {
        try {
            return UUID.fromString(Objects.requireNonNull(jwt, "jwt").getSubject());
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw new BusinessException(
                    AccountErrorCode.ADMIN_ACCESS_NOT_ALLOWED, exception);
        }
    }
}
