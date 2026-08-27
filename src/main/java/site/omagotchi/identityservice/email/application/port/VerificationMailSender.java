package site.omagotchi.identityservice.email.application.port;

import java.time.Duration;

public interface VerificationMailSender {

    void sendVerificationCode(
            String recipient,
            String verificationCode,
            String challengeId,
            Duration validity
    );
}
