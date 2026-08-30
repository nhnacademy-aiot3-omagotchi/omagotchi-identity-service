package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.account.application.AccountPasswordService;

import java.util.UUID;

/**
 * 인증된 사용자의 비밀번호 변경과 모든 Refresh Session 폐기를 하나의 Transaction으로 수행한다.
 */
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final AccountPasswordService accountPasswordService;
    private final AuthenticationEpochService authenticationEpochService;
    private final RefreshSessionRevocationService refreshSessionRevocationService;

    @Transactional
    public void changePassword(
            UUID accountId,
            String currentRawPassword,
            String newRawPassword
    ) {
        accountPasswordService.verifyAndReplacePasswordHash(
                accountId,
                currentRawPassword,
                newRawPassword
        );
        refreshSessionRevocationService.revokeAllForAccount(
                accountId,
                RefreshSessionRevocationReason.PASSWORD_CHANGED
        );
        // 비밀번호 변경에 따른 계정 전체 Access JWT 폐기
        authenticationEpochService.rotateForAccount(accountId);
    }
}
