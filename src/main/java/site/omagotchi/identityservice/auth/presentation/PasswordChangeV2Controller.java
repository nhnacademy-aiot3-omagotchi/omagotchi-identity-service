package site.omagotchi.identityservice.auth.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.auth.application.PasswordChangeV2Service;
import site.omagotchi.identityservice.auth.presentation.request.PasswordChangeV2Request;
import site.omagotchi.identityservice.emailverification.application.PasswordChangeEmailOtpService;
import site.omagotchi.identityservice.emailverification.presentation.response.EmailVerificationResponse;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/users/me/password")
@RequiredArgsConstructor
public class PasswordChangeV2Controller {

    private final PasswordChangeEmailOtpService emailOtpService;
    private final PasswordChangeV2Service passwordChangeService;

    @PostMapping("/email-otp")
    public ResponseEntity<EmailVerificationResponse> issueEmailOtp(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID accountId = accountId(jwt);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(EmailVerificationResponse.from(emailOtpService.issue(accountId)));
    }

    @PatchMapping
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeV2Request request
    ) {
        passwordChangeService.changePassword(
                accountId(jwt),
                request.currentPassword(),
                request.newPassword(),
                request.challengeId(),
                request.code()
        );
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private UUID accountId(Jwt jwt) {
        return UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    }
}
