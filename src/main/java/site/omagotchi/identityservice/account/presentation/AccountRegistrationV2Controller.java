package site.omagotchi.identityservice.account.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.account.application.AccountRegistrationV2Service;
import site.omagotchi.identityservice.account.application.result.AccountRegistrationResult;
import site.omagotchi.identityservice.account.presentation.request.SignupV2Request;
import site.omagotchi.identityservice.account.presentation.response.AccountResponse;

@RestController
@RequestMapping("/api/v2/auth/signup")
@RequiredArgsConstructor
public class AccountRegistrationV2Controller {

    private final AccountRegistrationV2Service accountRegistrationService;

    @PostMapping
    public ResponseEntity<AccountResponse> signUp(@Valid @RequestBody SignupV2Request request) {
        AccountRegistrationResult result = accountRegistrationService.signUp(
                request.email(),
                request.password(),
                request.name(),
                request.challengeId(),
                request.code()
        );
        HttpStatus status = result.outcome() == AccountRegistrationResult.Outcome.CREATED
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(AccountResponse.from(result.account()));
    }
}
