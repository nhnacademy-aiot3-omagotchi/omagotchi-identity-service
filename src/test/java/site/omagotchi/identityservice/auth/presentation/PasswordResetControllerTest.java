package site.omagotchi.identityservice.auth.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.auth.application.PasswordResetService;
import site.omagotchi.identityservice.auth.application.result.PasswordResetEmailOtpResult;
import site.omagotchi.identityservice.auth.presentation.request.PasswordResetEmailOtpRequest;
import site.omagotchi.identityservice.auth.presentation.request.PasswordResetRequest;
import site.omagotchi.identityservice.auth.presentation.response.PasswordResetEmailOtpResponse;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PasswordResetControllerTest {

    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000702401"
    );

    @Test
    @DisplayName("OTP 발급은 202와 no-store 응답")
    void returnsAcceptedWithoutCaching() {
        // Given
        PasswordResetService service = mock(PasswordResetService.class);
        PasswordResetController controller = new PasswordResetController(service);
        given(service.issueEmailOtp("member@example.com"))
                .willReturn(new PasswordResetEmailOtpResult(CHALLENGE_ID, 300));

        // When
        var response = controller.issueEmailOtp(
                new PasswordResetEmailOtpRequest("member@example.com")
        );

        // Then
        then(response.getStatusCode().value()).isEqualTo(202);
        then(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        then(response.getBody())
                .isEqualTo(new PasswordResetEmailOtpResponse(CHALLENGE_ID, 300));
    }

    @Test
    @DisplayName("비밀번호 재설정은 204와 no-store 응답")
    void returnsNoContentWithoutCaching() {
        // Given
        PasswordResetService service = mock(PasswordResetService.class);
        PasswordResetController controller = new PasswordResetController(service);
        PasswordResetRequest request = new PasswordResetRequest(
                "member@example.com",
                "new-password-passphrase",
                CHALLENGE_ID,
                "123456"
        );

        // When
        var response = controller.resetPassword(request);

        // Then
        then(response.getStatusCode().value()).isEqualTo(204);
        then(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(service).resetPassword(
                "member@example.com",
                "new-password-passphrase",
                CHALLENGE_ID,
                "123456"
        );
    }
}
