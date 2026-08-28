package site.omagotchi.identityservice.auth.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.presentation.request.SignupRequest;
import site.omagotchi.identityservice.account.presentation.request.SignupV2Request;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;
import site.omagotchi.identityservice.auth.presentation.request.PasswordChangeRequest;
import site.omagotchi.identityservice.auth.presentation.request.PasswordChangeV2Request;
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

    @Test
    @DisplayName("버전별 가입·비밀번호 변경 요청의 비밀번호와 OTP 마스킹")
    void redactsSensitiveFieldsAcrossApiVersions() {
        String password = "password-passphrase";
        String code = "123456";
        SignupRequest signup = new SignupRequest("user@example.com", password, "사용자");
        SignupV2Request signupV2 = new SignupV2Request(
                "user@example.com", password, "사용자", "challenge-id", code
        );
        PasswordChangeV2Request passwordChangeV2 = new PasswordChangeV2Request(
                password, "new-password-passphrase", "challenge-id", code
        );

        then(signup.toString()).doesNotContain(password);
        then(signupV2.toString()).doesNotContain(password, code);
        then(passwordChangeV2.toString()).doesNotContain(password, "new-password-passphrase", code);
    }
}
