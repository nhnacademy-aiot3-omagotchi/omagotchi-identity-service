package site.omagotchi.identityservice.auth.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.auth.application.PasswordResetService;
import site.omagotchi.identityservice.auth.presentation.request.PasswordResetEmailOtpRequest;
import site.omagotchi.identityservice.auth.presentation.request.PasswordResetRequest;
import site.omagotchi.identityservice.auth.presentation.response.PasswordResetEmailOtpResponse;

@RestController
@RequestMapping("/api/v2/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService service;

    @PostMapping("/email-otp")
    public ResponseEntity<PasswordResetEmailOtpResponse> issueEmailOtp(
            @Valid @RequestBody PasswordResetEmailOtpRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(PasswordResetEmailOtpResponse.from(
                        service.issueEmailOtp(request.email())
                ));
    }

    @PatchMapping
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        service.resetPassword(
                request.email(),
                request.newPassword(),
                request.challengeId(),
                request.code()
        );
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
