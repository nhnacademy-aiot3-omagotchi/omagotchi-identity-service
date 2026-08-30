package site.omagotchi.identityservice.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.identityservice.auth.application.port.AuthenticationEpochStore;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationEpochService {

    private final AuthenticationEpochStore authenticationEpochStore;

    // 로그인 경로에 한정된 누락 Epoch 원자 생성
    public UUID getOrCreateForLogin(UUID accountId) {
        return authenticationEpochStore.find(accountId)
                .orElseGet(() -> authenticationEpochStore.createIfAbsent(
                        accountId,
                        UUID.randomUUID()
                ));
    }

    // Refresh 경로의 누락 Epoch 자동 생성 금지
    public UUID getRequiredForRefresh(UUID accountId) {
        return authenticationEpochStore.find(accountId)
                .orElseThrow(() -> new BusinessException(
                        AuthErrorCode.INVALID_REFRESH_TOKEN
                ));
    }

    /**
     * 계정 전체 Access JWT 폐기용 Authentication Epoch 교체
     */
    public UUID rotateForAccount(UUID accountId) {
        UUID nextEpoch = UUID.randomUUID();
        authenticationEpochStore.replace(accountId, nextEpoch);
        return nextEpoch;
    }
}
