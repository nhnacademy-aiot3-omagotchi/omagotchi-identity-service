package site.omagotchi.identityservice.account.presentation.response;

import site.omagotchi.identityservice.account.application.result.SignupEmailOtpResult;

import java.util.UUID;

public record SignupEmailOtpResponse(
        UUID challengeId,
        long expiresInSeconds
) {
    public static SignupEmailOtpResponse from(SignupEmailOtpResult issued) {
        return new SignupEmailOtpResponse(issued.challengeId(), issued.expiresInSeconds());
    }
}
