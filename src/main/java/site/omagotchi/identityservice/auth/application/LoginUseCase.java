package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountReader;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.auth.application.dto.LoginResult;
import site.omagotchi.identityservice.auth.domain.AuthErrorCode;
import site.omagotchi.identityservice.auth.infrastructure.AccessTokenIssuer;
import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.security.JwtProperties;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final AccountReader accountReader;
    private final CredentialVerifier credentialVerifier;
    private final AccessTokenIssuer accessTokenIssuer;
    private final JwtProperties jwtProperties;

    @Transactional(readOnly = true)
    public LoginResult execute(String email, String password) {
        Account account = accountReader.findByEmail(email).orElse(null);
        boolean passwordMatches = credentialVerifier.matches(account, password);

        if (account == null || !account.isLoginAllowed() || !passwordMatches) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = accessTokenIssuer.issue(account);
        return new LoginResult(accessToken, jwtProperties.accessTokenTtl().toSeconds());
    }
}
