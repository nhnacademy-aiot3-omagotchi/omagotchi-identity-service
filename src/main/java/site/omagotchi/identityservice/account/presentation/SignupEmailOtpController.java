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
import site.omagotchi.identityservice.account.presentation.request.SignupEmailOtpRequest;
import site.omagotchi.identityservice.account.presentation.response.SignupEmailOtpResponse;

@RestController
@RequestMapping("/api/v2/auth/signup/email-otp")
@RequiredArgsConstructor
public class SignupEmailOtpController {

    private final AccountRegistrationV2Service service;

    @PostMapping
    public ResponseEntity<SignupEmailOtpResponse> issue(
            @Valid @RequestBody SignupEmailOtpRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(SignupEmailOtpResponse.from(service.issueEmailOtp(
                        request.email(),
                        request.password(),
                        request.name()
                )));
    }
}
