package site.omagotchi.identityservice.accountrole.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.accountrole.application.AccountRoleChangeService;
import site.omagotchi.identityservice.accountrole.application.AdminGlobalRole;
import site.omagotchi.identityservice.accountrole.presentation.request.ChangeAccountRoleRequest;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/accounts/{user-id}/role")
@RequiredArgsConstructor
public class AdminAccountRoleController {

    private final AccountRoleChangeService accountRoleChangeService;

    @PatchMapping
    public ResponseEntity<Void> changeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("user-id") UUID userId,
            @Valid @RequestBody ChangeAccountRoleRequest request
    ) {
        accountRoleChangeService.changeGlobalRole(
                UUID.fromString(Objects.requireNonNull(jwt.getSubject())),
                userId,
                toApplicationRole(request.role()),
                request.reason()
        );
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private AdminGlobalRole toApplicationRole(ChangeAccountRoleRequest.TargetRole role) {
        return switch (role) {
            case USER -> AdminGlobalRole.USER;
            case SYSTEM_ADMIN -> AdminGlobalRole.SYSTEM_ADMIN;
        };
    }
}
