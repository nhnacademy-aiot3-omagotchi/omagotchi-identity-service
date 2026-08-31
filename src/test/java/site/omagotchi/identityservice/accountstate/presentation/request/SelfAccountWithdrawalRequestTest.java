package site.omagotchi.identityservice.accountstate.presentation.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class SelfAccountWithdrawalRequestTest {

    @Test
    @DisplayName("본인 탈퇴 요청의 현재 비밀번호 원문 마스킹")
    void redactsCurrentPasswordFromStringRepresentation() {
        // Given
        String currentPassword = "withdrawal-password-passphrase";

        // When
        SelfAccountWithdrawalRequest request = new SelfAccountWithdrawalRequest(
                currentPassword
        );
        String stringRepresentation = request.toString();

        // Then
        then(stringRepresentation)
                .contains("[REDACTED]")
                .doesNotContain(currentPassword);
    }
}
