package site.omagotchi.identityservice.auth.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
import site.omagotchi.identityservice.auth.presentation.request.PasswordChangeRequest;
import site.omagotchi.identityservice.auth.presentation.request.RefreshTokenRequest;
import site.omagotchi.identityservice.auth.presentation.response.TokenResponse;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;

class AuthSensitiveValueTest {

    @Test
    @DisplayName("내부 인증 요청·응답의 Token 원문 마스킹")
    void redactsRawTokensFromStringRepresentations() {
        // Given
        String accessToken = "raw-access-token";
        String refreshToken = "raw-refresh-token";
        Instant accessExpiresAt = Instant.parse("2026-08-02T12:15:00Z");
        Instant refreshExpiresAt = Instant.parse("2026-08-09T12:00:00Z");
        TokenIssueResult result = new TokenIssueResult(
                UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1"),
                "USER",
                accessToken,
                accessExpiresAt,
                refreshToken,
                refreshExpiresAt
        );
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest(
                "current-password-passphrase",
                "new-password-passphrase"
        );
        TokenResponse response = TokenResponse.from(result);

        // When
        String resultText = result.toString();
        String requestText = request.toString();
        String passwordChangeRequestText = passwordChangeRequest.toString();
        String responseText = response.toString();

        // Then
        then(resultText)
                .contains("[REDACTED]")
                .doesNotContain(accessToken, refreshToken);
        then(requestText).contains("[REDACTED]").doesNotContain(refreshToken);
        then(passwordChangeRequestText)
                .contains("[REDACTED]")
                .doesNotContain(
                        passwordChangeRequest.currentPassword(),
                        passwordChangeRequest.newPassword()
                );
        then(responseText)
                .contains("[REDACTED]")
                .doesNotContain(accessToken, refreshToken);
    }
}
