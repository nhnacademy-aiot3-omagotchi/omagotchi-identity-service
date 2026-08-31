package site.omagotchi.identityservice.emailverification.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.emailverification.application.SignupEmailOtpService;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.emailverification.presentation.request.SignupEmailOtpRequest;
import site.omagotchi.identityservice.emailverification.presentation.response.EmailVerificationResponse;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SignupEmailOtpControllerTest {

    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700202"
    );

    @Test
    @DisplayName("회원가입 OTP 발급은 202와 no-store 응답")
    void returnsAcceptedWithoutCaching() {
        // Given
        SignupEmailOtpService service = mock(SignupEmailOtpService.class);
        SignupEmailOtpController controller = new SignupEmailOtpController(service);
        UUID challengeId = CHALLENGE_ID;
        given(service.issue("member@example.com", "long-enough-password", "member"))
                .willReturn(new IssuedEmailVerification(challengeId, 300));

        // When
        var response = controller.issue(new SignupEmailOtpRequest(
                "member@example.com", "long-enough-password", "member"
        ));

        // Then
        then(response.getStatusCode().value()).isEqualTo(202);
        then(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        then(response.getBody()).isEqualTo(new EmailVerificationResponse(challengeId, 300));
    }
}
