package site.omagotchi.identityservice.emailverification.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import site.omagotchi.identityservice.emailverification.application.EmailDeliveryException;
import site.omagotchi.identityservice.emailverification.application.EmailDeliveryFailureKind;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailVerificationMailSenderTest {

    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700200"
    );

    @Test
    @DisplayName("classpath HTML 템플릿을 렌더링하여 Resend 요청")
    void rendersClasspathHtmlTemplate() {
        // Given
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailVerificationMailSender sender = sender(builder.build());
        server.expect(requestTo("https://api.resend.test/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
                .andExpect(header("Idempotency-Key", CHALLENGE_ID.toString()))
                .andExpect(jsonPath("$.text")
                        .value("인증번호는 123456입니다. 5분 안에 입력해 주세요."))
                .andExpect(jsonPath("$.html").value(allOf(
                        containsString("<!doctype html>"),
                        containsString("cid:omagotchi-mascot"),
                        containsString(">1</td>"),
                        containsString(">6</td>"),
                        not(containsString("th:each"))
                )))
                .andExpect(jsonPath("$.attachments[0].content_id")
                        .value("omagotchi-mascot"))
                .andRespond(withSuccess());

        // When
        sender.sendVerificationCode(
                CHALLENGE_ID,
                "member@example.com",
                "123456",
                Duration.ofMinutes(5)
        );

        // Then
        server.verify();
    }

    @Test
    @DisplayName("Resend 429를 Rate Limit 메일 실패로 변환")
    void mapsTooManyRequestsToRateLimitedFailure() {
        // Given
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailVerificationMailSender sender = sender(builder.build());
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

    private ResendEmailVerificationMailSender sender(RestClient restClient) {
        return new ResendEmailVerificationMailSender(
                restClient,
                new ResendProperties(
                        "test-api-key",
                        "no-reply@omagotchi.test",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)
                )
        );
    }
}
