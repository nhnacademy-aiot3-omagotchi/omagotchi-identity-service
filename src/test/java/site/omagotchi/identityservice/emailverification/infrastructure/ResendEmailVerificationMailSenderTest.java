package site.omagotchi.identityservice.emailverification.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import site.omagotchi.identityservice.emailverification.application.EmailDeliveryException;
import site.omagotchi.identityservice.emailverification.application.EmailDeliveryFailureKind;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ResendEmailVerificationMailSenderTest {

    @Test
    @DisplayName("Resend 429를 Rate Limit 메일 실패로 변환")
    void mapsTooManyRequestsToRateLimitedFailure() {
        // Given
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailVerificationMailSender sender = new ResendEmailVerificationMailSender(
                builder.build(),
                new ResendProperties(
                        "test-api-key",
                        "no-reply@omagotchi.test",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)
                )
        );
        server.expect(requestTo("https://api.resend.test/emails"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // When
        // Then
        thenThrownBy(() -> sender.sendVerificationCode(
                UUID.fromString("00000000-0000-0000-0000-000000700429"),
                "member@example.com",
                "123456",
                Duration.ofMinutes(5)
        )).isInstanceOfSatisfying(
                EmailDeliveryException.class,
                exception -> then(exception.failureKind())
                        .isEqualTo(EmailDeliveryFailureKind.RATE_LIMITED)
        );
        server.verify();
    }
}
