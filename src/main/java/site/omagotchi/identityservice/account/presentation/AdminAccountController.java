package site.omagotchi.identityservice.account.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.account.application.AccountAdminQueryService;
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.AdminAccessGuard;
import site.omagotchi.identityservice.account.application.port.AccountPage;
import site.omagotchi.identityservice.account.presentation.request.AdminAccountSearchRequest;
import site.omagotchi.identityservice.account.presentation.response.AdminAccountPageResponse;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Objects;
import java.util.UUID;

/*
 * 전역 운영 관리자의 사용자 목록 조회 경계
 *
 * TODO(admin-command): 권한·상태 변경 API를 추가할 때 아래를 함께 넣지 않으면
 *   시스템에서 SYSTEM_ADMIN이 0명이 될 수 있다.
 *   1) 자기 자신의 role·status 변경 금지
 *   2) 활성 SYSTEM_ADMIN이 1명이면 강등·비활성 차단
 *   3) 관리자가 2명 이상이 되는 시점부터는 (1)(2)만으로 부족하다.
 *      서로를 동시에 강등하면 두 요청이 모두 인원수 검사를 통과하므로
 *      Advisory Lock 등으로 권한 변경 구간을 직렬화해야 한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountAdminQueryService accountAdminQueryService;
    private final AdminAccessGuard adminAccessGuard;

    @GetMapping
    public ResponseEntity<AdminAccountPageResponse> getUsers(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute AdminAccountSearchRequest request
    ) {
        // Filter Chain의 role Claim 검사 이후 요청 시점 DB 권한으로 최종 확인
        UUID actorId = adminAccessGuard.requireSystemAdmin(actorIdOf(jwt));

        int page = request.pageOrDefault();
        int size = request.sizeOrDefault();
        AccountPage accountPage = accountAdminQueryService.search(
                request.query(),
                request.status(),
                request.role(),
                page,
                size,
                request.sortOrDefault()
        );

        // 전체 계정을 열람할 수 있는 유일한 경로이므로 호출 사실을 남긴다.
        log.info(
                "관리자 사용자 목록 조회 actorId={}, page={}, size={}, keywordLength={}, total={}",
                actorId,
                page,
                size,
                request.keywordLength(),
                accountPage.totalElements()
        );

        return ResponseEntity.ok(AdminAccountPageResponse.of(accountPage, page, size));
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
