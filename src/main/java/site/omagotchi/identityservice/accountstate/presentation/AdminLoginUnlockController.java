package site.omagotchi.identityservice.accountstate.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.accountstate.application.AdminLoginUnlockService;
import site.omagotchi.identityservice.accountstate.presentation.request.LoginUnlockRequest;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/accounts/{user-id}/login-lock/unlock")
@RequiredArgsConstructor
public class AdminLoginUnlockController {

    private final AdminLoginUnlockService adminLoginUnlockService;

    @PostMapping
    public ResponseEntity<Void> unlockLogin(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("user-id") UUID userId,
            @Valid @RequestBody LoginUnlockRequest request
    ) {
        adminLoginUnlockService.unlockLogin(
                UUID.fromString(Objects.requireNonNull(jwt.getSubject())),
                userId,
                request.reason()
        );
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
