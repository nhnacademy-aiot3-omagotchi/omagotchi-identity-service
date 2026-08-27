package site.omagotchi.identityservice.account.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.account.application.AccountProfileService;
import site.omagotchi.identityservice.account.application.AccountQueryService;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.presentation.request.UpdateAccountRequest;
import site.omagotchi.identityservice.account.presentation.response.AccountResponse;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class SelfAccountController {

    private final AccountQueryService accountQueryService;
    private final AccountProfileService accountProfileService;

    @GetMapping
    public ResponseEntity<AccountResponse> me(@AuthenticationPrincipal Jwt jwt) {
        // JwtDecoder의 UUID 형식 sub 검증 이후 nullable API 경계의 명시적 방어
        Account account = accountQueryService.getById(
                UUID.fromString(Objects.requireNonNull(jwt.getSubject()))
        );
        return ResponseEntity.ok(AccountResponse.from(account));
    }

    @PatchMapping
    public ResponseEntity<Void> updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        accountProfileService.changeName(
                UUID.fromString(Objects.requireNonNull(jwt.getSubject())),
                request.name()
        );
        return ResponseEntity.noContent().build();
    }
}
