package site.omagotchi.identityservice.emailverification.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.emailverification.application.SignupEmailOtpService;
import site.omagotchi.identityservice.emailverification.presentation.request.SignupEmailOtpRequest;
import site.omagotchi.identityservice.emailverification.presentation.response.EmailVerificationResponse;

@RestController
@RequestMapping("/api/v2/auth/signup/email-otp")
@RequiredArgsConstructor
public class SignupEmailOtpController {

    private final SignupEmailOtpService service;

    @PostMapping
    public ResponseEntity<EmailVerificationResponse> issue(
            @Valid @RequestBody SignupEmailOtpRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(EmailVerificationResponse.from(service.issue(
                        request.email(),
                        request.password(),
                        request.name()
                )));
    }
}
