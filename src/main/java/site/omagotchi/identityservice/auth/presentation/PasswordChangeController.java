package site.omagotchi.identityservice.auth.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.auth.application.PasswordChangeService;
import site.omagotchi.identityservice.auth.presentation.request.PasswordChangeRequest;
import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;
import site.omagotchi.identityservice.email.presentation.response.EmailVerificationChallengeResponse;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/password")
@RequiredArgsConstructor
public class PasswordChangeController {

    private final PasswordChangeService passwordChangeService;

    @PostMapping("/email-verification/challenges")
    public ResponseEntity<EmailVerificationChallengeResponse> requestEmailVerification(
            @AuthenticationPrincipal Jwt jwt
    ) {
        EmailVerificationChallengeResult result = passwordChangeService
                .requestEmailVerification(accountId(jwt));
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(EmailVerificationChallengeResponse.from(result));
    }

    @PatchMapping
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request
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
