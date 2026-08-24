package site.omagotchi.identityservice.auth.application.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountAuthenticationService;
import site.omagotchi.identityservice.auth.application.port.RefreshTokenRepository;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshSessionRevocationService {

    private final AccountAuthenticationService accountAuthenticationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Transactional
    public void revokeAllForAccount(
            UUID accountId,
            RefreshSessionRevocationReason reason
    ) {
        UUID targetAccountId = Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(reason, "reason");

        // 로그인·Refresh·Logout과 동일한 계정 행 선점에 의한 새 Token 발급 직렬화
        accountAuthenticationService.lockAuthenticationById(targetAccountId);
        refreshTokenRepository.revokeAllByAccountId(
                targetAccountId,
                clock.instant(),
                toTokenRevocationReason(reason)
        );
    }

    private RefreshTokenRevocationReason toTokenRevocationReason(
            RefreshSessionRevocationReason reason
    ) {
        return switch (reason) {
            case PASSWORD_CHANGED -> RefreshTokenRevocationReason.PASSWORD_CHANGED;
            case PASSWORD_RESET -> RefreshTokenRevocationReason.PASSWORD_RESET;
            case ACCOUNT_DISABLED -> RefreshTokenRevocationReason.ACCOUNT_DISABLED;
            case ACCOUNT_WITHDRAWN -> RefreshTokenRevocationReason.ACCOUNT_WITHDRAWN;
        };
    }
}
