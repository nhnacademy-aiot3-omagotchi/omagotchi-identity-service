package site.omagotchi.identityservice.accountstate.presentation;

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
import site.omagotchi.identityservice.accountstate.application.AdminAccountStatusChangeService;
import site.omagotchi.identityservice.accountstate.presentation.request.ChangeAccountStatusRequest;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/accounts/{user-id}/status")
@RequiredArgsConstructor
public class AdminAccountStatusController {

    private final AdminAccountStatusChangeService accountStatusChangeService;

    @PatchMapping
    public ResponseEntity<Void> changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("user-id") UUID userId,
            @Valid @RequestBody ChangeAccountStatusRequest request
    ) {
        accountStatusChangeService.changeStatus(
                UUID.fromString(Objects.requireNonNull(jwt.getSubject())),
                userId,
                request.toAdminAccountStatus(),
                request.reason()
        );
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
