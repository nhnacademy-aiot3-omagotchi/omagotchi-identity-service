package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountReader;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.auth.application.dto.TokenIssueResult;
import site.omagotchi.identityservice.auth.domain.AuthErrorCode;
import site.omagotchi.identityservice.auth.infrastructure.AccessTokenIssuer;
import site.omagotchi.identityservice.auth.infrastructure.IssuedAccessToken;
import site.omagotchi.identityservice.auth.infrastructure.IssuedRefreshToken;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenIssuer;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenStore;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final AccountReader accountReader;
    private final CredentialVerifier credentialVerifier;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    @Transactional
    public TokenIssueResult execute(String email, String password) {
        Account account = accountReader.findByEmail(email).orElse(null);
        boolean passwordMatches = credentialVerifier.matches(account, password);

        if (account == null || !account.isLoginAllowed() || !passwordMatches) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Instant issuedAt = clock.instant();
        IssuedRefreshToken refreshToken = refreshTokenIssuer.issueNewFamily(account.getId(), issuedAt);
        refreshTokenStore.save(refreshToken.refreshToken());

        IssuedAccessToken accessToken = accessTokenIssuer.issue(account);
        return new TokenIssueResult(
                accessToken.value(),
                accessToken.expiresInSeconds(),
                refreshToken.value(),
                refreshToken.refreshToken().getExpiresAt()
        );
    }
}
