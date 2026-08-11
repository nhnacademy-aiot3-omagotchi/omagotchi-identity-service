package site.omagotchi.identityservice.account.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.account.application.AccountQueryService;
import site.omagotchi.identityservice.account.application.AccountRegistrationService;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.presentation.request.SignupRequest;
import site.omagotchi.identityservice.account.presentation.response.AccountResponse;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRegistrationService accountRegistrationService;
    private final AccountQueryService accountQueryService;

    @PostMapping("/auth/signup")
    public ResponseEntity<AccountResponse> signUp(@Valid @RequestBody SignupRequest request) {
        Account account = accountRegistrationService.signUp(
                request.email(),
                request.password(),
                request.name()
        );
        // AccountResponse의 application/json 직렬화 반환
        // 사용자 입력값을 HTML에 직접 삽입하지 않는 REST 경계로 XSS sink 경고 비해당
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AccountResponse.from(account));
    }

    @GetMapping("/users/me")
    public ResponseEntity<AccountResponse> me(@AuthenticationPrincipal Jwt jwt) {
        // JwtDecoder의 UUID 형식 sub 검증 이후 nullable API 경계의 명시적 방어
        Account account = accountQueryService.getById(
                UUID.fromString(Objects.requireNonNull(jwt.getSubject()))
        );
        return ResponseEntity.ok(AccountResponse.from(account));
    }
}
