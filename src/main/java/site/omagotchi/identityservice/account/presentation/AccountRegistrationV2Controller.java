package site.omagotchi.identityservice.account.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.account.application.AccountRegistrationV2Service;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.presentation.request.SignupEmailOtpRequest;
import site.omagotchi.identityservice.account.presentation.request.SignupV2Request;
import site.omagotchi.identityservice.account.presentation.response.AccountResponse;
import site.omagotchi.identityservice.account.presentation.response.SignupEmailOtpResponse;
import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;

@RestController
@RequestMapping("/api/v2/auth/signup")
@RequiredArgsConstructor
public class AccountRegistrationV2Controller {

    private final AccountRegistrationV2Service accountRegistrationService;

    @PostMapping("/email-otp")
    public ResponseEntity<SignupEmailOtpResponse> requestEmailOtp(
            @Valid @RequestBody SignupEmailOtpRequest request
    ) {
        EmailVerificationChallengeResult result = accountRegistrationService
                .requestEmailOtp(
                        request.email(),
                        request.password(),
                        request.name()
                );
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(SignupEmailOtpResponse.from(result));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> signUp(@Valid @RequestBody SignupV2Request request) {
        Account account = accountRegistrationService.signUp(
                request.email(),
                request.password(),
                request.name(),
                request.challengeId(),
                request.code()
        );
        // AccountResponse의 application/json 직렬화 반환
        // 사용자 입력값을 HTML에 직접 삽입하지 않는 REST 경계로 XSS sink 경고 비해당
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AccountResponse.from(account));
    }
}
