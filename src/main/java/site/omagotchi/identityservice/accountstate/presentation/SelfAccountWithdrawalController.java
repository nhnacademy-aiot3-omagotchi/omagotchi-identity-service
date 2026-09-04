package site.omagotchi.identityservice.accountstate.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.accountstate.application.SelfAccountWithdrawalService;
import site.omagotchi.identityservice.accountstate.presentation.request.SelfAccountWithdrawalRequest;
import site.omagotchi.identityservice.accountstate.presentation.response.SelfAccountWithdrawalResponse;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class SelfAccountWithdrawalController {

    private final SelfAccountWithdrawalService selfAccountWithdrawalService;

    @DeleteMapping
    public ResponseEntity<SelfAccountWithdrawalResponse> withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SelfAccountWithdrawalRequest request
    ) {
        SelfAccountWithdrawalResponse response = new SelfAccountWithdrawalResponse(
                selfAccountWithdrawalService.withdraw(
                        UUID.fromString(Objects.requireNonNull(jwt.getSubject())),
                        request.currentPassword()
                )
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
