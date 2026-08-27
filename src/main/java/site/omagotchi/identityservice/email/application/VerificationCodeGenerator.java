package site.omagotchi.identityservice.email.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class VerificationCodeGenerator {

    private static final int CODE_UPPER_BOUND = 1_000_000;

    private final SecureRandom secureRandom;

    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(CODE_UPPER_BOUND));
    }
}
