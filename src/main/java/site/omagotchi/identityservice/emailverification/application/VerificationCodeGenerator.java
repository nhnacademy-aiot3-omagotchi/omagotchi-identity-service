package site.omagotchi.identityservice.emailverification.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class VerificationCodeGenerator {

    static final int CODE_BOUND = 1_000_000;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(CODE_BOUND));
    }
}
