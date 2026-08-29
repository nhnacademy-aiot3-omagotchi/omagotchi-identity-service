package site.omagotchi.identityservice.auth.application.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountAuthenticationService;
import site.omagotchi.identityservice.account.application.result.AccountAuthenticationResult;
import site.omagotchi.identityservice.auth.application.AuthenticationEpochService;
import site.omagotchi.identityservice.auth.application.port.AccessTokenIssuer;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.application.result.IssuedAccessToken;
import site.omagotchi.identityservice.auth.application.result.IssuedRefreshToken;
import site.omagotchi.identityservice.auth.application.result.TokenIssueResult;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LoginTransaction {

    private final AccountAuthenticationService accountAuthenticationService;
    private final AuthenticationEpochService authenticationEpochService;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /*
     * 실패 횟수 변경과 성공 Token 발급의 단일 Transaction 경계
     * Optional.empty 정상 반환을 통한 인증 실패 기록 Commit
     * Transaction 종료 후 외부 AuthenticationService의 공개 인증 오류 변환
     */
    @Transactional
    public Optional<TokenIssueResult> login(String email, String rawPassword) {
        Optional<AccountAuthenticationResult> authenticatedAccount =
                accountAuthenticationService.authenticate(email, rawPassword);
        if (authenticatedAccount.isEmpty()) {
            return Optional.empty();
        }

        AccountAuthenticationResult account = authenticatedAccount.get();
        // Token 발급 전 Epoch 확보를 통한 Redis 장애 시 Fail-closed
        UUID authenticationEpoch = authenticationEpochService.getOrCreateForLogin(
                account.accountId()
        );
        Instant issuedAt = clock.instant();
        IssuedRefreshToken refreshToken = refreshTokenIssuer.issueNewFamily(
                account.accountId(),
                issuedAt
        );
        refreshTokenRepository.store(refreshToken.refreshToken());

        IssuedAccessToken accessToken = accessTokenIssuer.issue(
                account.accountId(),
                account.globalRole(),
                authenticationEpoch
        );
        return Optional.of(new TokenIssueResult(
                account.accountId(),
                account.globalRole(),
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken.value(),
                refreshToken.refreshToken().getExpiresAt()
        ));
    }
}
