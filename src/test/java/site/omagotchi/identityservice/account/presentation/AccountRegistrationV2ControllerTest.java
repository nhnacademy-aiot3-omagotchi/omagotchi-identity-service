package site.omagotchi.identityservice.account.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.identityservice.account.application.AccountRegistrationV2Service;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.presentation.request.SignupV2Request;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AccountRegistrationV2ControllerTest {

    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700201"
    );

    @Test
    @DisplayName("이메일 인증 회원가입 성공은 201 응답")
    void returnsCreatedAccount() {
        // Given
        AccountRegistrationV2Service service = mock(AccountRegistrationV2Service.class);
        AccountRegistrationV2Controller controller = new AccountRegistrationV2Controller(service);
        UUID challengeId = CHALLENGE_ID;
        Account account = Account.register("member@example.com", "password-hash", "member");
        given(service.signUp(
                "member@example.com", "long-enough-password", "member", challengeId, "123456"
        )).willReturn(account);

        // When
        var response = controller.signUp(new SignupV2Request(
                "member@example.com",
                "long-enough-password",
                "member",
                challengeId,
                "123456"
        ));

        // Then
        then(response.getStatusCode().value()).isEqualTo(201);
        then(response.getBody().email()).isEqualTo("member@example.com");
    }
}
