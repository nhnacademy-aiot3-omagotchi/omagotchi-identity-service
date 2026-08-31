package site.omagotchi.identityservice.emailverification.application.port;

import java.time.Duration;
import java.util.UUID;

public interface EmailVerificationMailSender {

    void sendVerificationCode(
            UUID challengeId,
            String recipient,
            String code,
            Duration validity
    );
}
