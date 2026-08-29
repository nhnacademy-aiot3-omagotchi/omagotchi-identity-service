package site.omagotchi.identityservice.auth.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.auth.application.port.AuthenticationEpochStore;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthenticationEpochServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000635"
    );

    @Test
    @DisplayName("로그인 시 기존 Authentication Epoch 반환")
    void returnsExistingEpochForLogin() {
        // Given
        AuthenticationEpochStore store = mock(AuthenticationEpochStore.class);
        AuthenticationEpochService service = new AuthenticationEpochService(store);
        UUID existingEpoch = UUID.randomUUID();
        given(store.find(ACCOUNT_ID)).willReturn(Optional.of(existingEpoch));

        // When
        UUID result = service.getOrCreateForLogin(ACCOUNT_ID);

        // Then
        then(result).isEqualTo(existingEpoch);
        verify(store, never()).createIfAbsent(any(), any());
    }

    @Test
    @DisplayName("로그인 시 미존재 Authentication Epoch 원자 생성")
    void createsMissingEpochForLogin() {
        // Given
        AuthenticationEpochStore store = mock(AuthenticationEpochStore.class);
        AuthenticationEpochService service = new AuthenticationEpochService(store);
        UUID createdEpoch = UUID.randomUUID();
        given(store.find(ACCOUNT_ID)).willReturn(Optional.empty());
        given(store.createIfAbsent(any(), any())).willReturn(createdEpoch);

        // When
        UUID result = service.getOrCreateForLogin(ACCOUNT_ID);

        // Then
        then(result).isEqualTo(createdEpoch);
        verify(store).createIfAbsent(eq(ACCOUNT_ID), any(UUID.class));
    }

    @Test
    @DisplayName("Refresh 시 Authentication Epoch 미존재를 인증 실패로 변환")
    void rejectsRefreshWhenEpochIsMissing() {
        // Given
        AuthenticationEpochStore store = mock(AuthenticationEpochStore.class);
        AuthenticationEpochService service = new AuthenticationEpochService(store);
        given(store.find(ACCOUNT_ID)).willReturn(Optional.empty());

        // When
        Throwable thrown = catchThrowable(() -> service.getRequiredForRefresh(ACCOUNT_ID));

        // Then
        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN)
        );
        verify(store, never()).createIfAbsent(any(), any());
    }

    @Test
    @DisplayName("계정 전체 Access JWT 폐기를 위한 Authentication Epoch 교체")
    void rotatesEpochForAccount() {
        // Given
        AuthenticationEpochStore store = mock(AuthenticationEpochStore.class);
        AuthenticationEpochService service = new AuthenticationEpochService(store);

        // When
        UUID nextEpoch = service.rotateForAccount(ACCOUNT_ID);

        // Then
        then(nextEpoch).isNotNull();
        verify(store).replace(ACCOUNT_ID, nextEpoch);
    }
}
