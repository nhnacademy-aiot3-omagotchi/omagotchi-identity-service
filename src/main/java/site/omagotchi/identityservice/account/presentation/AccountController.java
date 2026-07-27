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
import site.omagotchi.identityservice.account.application.AccountReader;
import site.omagotchi.identityservice.account.application.SignupAccountUseCase;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.presentation.dto.AccountResponse;
import site.omagotchi.identityservice.account.presentation.dto.SignupRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountController {

    private final SignupAccountUseCase signupAccountUseCase;
    private final AccountReader accountReader;

    @PostMapping("/auth/signup")
    public ResponseEntity<AccountResponse> signup(@Valid @RequestBody SignupRequest request) {
        Account account = signupAccountUseCase.execute(request.toCommand());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AccountResponse.from(account));
    }

    @GetMapping("/users/me")
    public ResponseEntity<AccountResponse> me(@AuthenticationPrincipal Jwt jwt) {
        Account account = accountReader.readById(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(AccountResponse.from(account));
    }
}
