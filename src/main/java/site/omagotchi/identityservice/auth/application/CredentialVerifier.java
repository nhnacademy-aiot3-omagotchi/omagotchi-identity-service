package site.omagotchi.identityservice.auth.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.account.domain.Account;

import java.util.UUID;

@Component
public class CredentialVerifier {

    private final PasswordEncoder passwordEncoder;
    private final String fallbackPasswordHash;

    public CredentialVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.fallbackPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public boolean matches(Account account, String rawPassword) {
        String passwordHash = account == null
                ? fallbackPasswordHash
                : account.getPasswordHash();

        return passwordEncoder.matches(
                rawPassword == null ? "" : rawPassword,
                passwordHash
        );
    }
}
