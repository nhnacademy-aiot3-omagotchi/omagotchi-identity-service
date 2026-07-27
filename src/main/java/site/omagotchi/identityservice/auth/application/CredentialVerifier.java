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
        // 계정이 없어도 BCrypt 검증을 수행하기 위한 임의의 Hash
        // 계정이 없을 때만 빠르게 실패하면 응답 시간 차이로 가입된 이메일을 추측하기 쉬워짐
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
