package site.omagotchi.identityservice.emailverification.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import site.omagotchi.identityservice.emailverification.application.EmailDeliveryException;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ResendEmailVerificationMailSender implements EmailVerificationMailSender {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final ResendProperties properties;

    public ResendEmailVerificationMailSender(
            @Qualifier("resendRestClient") RestClient restClient,
            ResendProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public void sendVerificationCode(
            UUID challengeId,
            String recipient,
            String code,
            Duration validity
    ) {
        Map<String, Object> request = Map.of(
                "from", properties.fromEmail(),
                "to", List.of(recipient),
                "subject", "[오마고치] 이메일 인증번호",
                "text", "인증번호는 " + code + "입니다. "
                        + validity.toMinutes() + "분 안에 입력해 주세요."
        );

        try {
            restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .header(IDEMPOTENCY_KEY_HEADER, challengeId.toString())
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new EmailDeliveryException("Resend 메일 전송 요청에 실패했습니다.", exception);
        }
    }
}
