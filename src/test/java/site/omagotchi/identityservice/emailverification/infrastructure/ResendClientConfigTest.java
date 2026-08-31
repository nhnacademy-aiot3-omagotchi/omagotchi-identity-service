package site.omagotchi.identityservice.emailverification.infrastructure;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import site.omagotchi.identityservice.emailverification.application.EmailDeliveryException;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationProperties;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class ResendClientConfigTest {

    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000700601"
    );

    @Test
    @DisplayName("Resend 지연 응답은 read timeout 안에 실패")
    void timesOutDelayedResponse() throws IOException {
        // Given
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/emails", exchange -> {
            try {
                Thread.sleep(Duration.ofSeconds(2));
                exchange.sendResponseHeaders(202, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Client timeout 뒤 닫힌 연결에 쓰는 실패는 이 테스트의 기대 결과다.
            } finally {
                exchange.close();
            }
        });
        server.start();

        ResendProperties properties = properties(
                Duration.ofMillis(100),
                Duration.ofMillis(100)
        );
        RestClient client = RestClient.builder()
                .baseUrl("http://" + server.getAddress().getHostString()
                        + ":" + server.getAddress().getPort())
                .requestFactory(ResendClientConfig.createRequestFactory(properties))
                .build();
        ResendEmailVerificationMailSender sender =
                new ResendEmailVerificationMailSender(client, properties);

        // When
        long startedAt = System.nanoTime();
        try {
            // Then
            thenThrownBy(() -> sender.sendVerificationCode(
                    CHALLENGE_ID,
                    "member@example.com",
                    "123456",
                    Duration.ofMinutes(5)
            )).isInstanceOf(EmailDeliveryException.class)
                    .hasMessage("Resend 메일 전송 요청에 실패했습니다.");
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            then(elapsedMillis).isLessThan(1_500);
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("connect timeout이 read timeout보다 길면 기동 정책 거부")
    void rejectsConnectTimeoutLongerThanReadTimeout() {
        // Given
        ResendProperties properties = properties(
                Duration.ofSeconds(6),
                Duration.ofSeconds(5)
        );

        // When
        // Then
        thenThrownBy(() -> ResendClientConfig.validateTimeoutPolicy(
                properties,
                verificationProperties(Duration.ofMinutes(5))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("email.resend.connect-timeout은 read-timeout보다 길 수 없습니다.");
    }

    @Test
    @DisplayName("read timeout이 OTP TTL 이상이면 기동 정책 거부")
    void rejectsReadTimeoutNotShorterThanCodeTtl() {
        // Given
        ResendProperties properties = properties(
                Duration.ofSeconds(2),
                Duration.ofMinutes(5)
        );

        // When
        // Then
        thenThrownBy(() -> ResendClientConfig.validateTimeoutPolicy(
                properties,
                verificationProperties(Duration.ofMinutes(5))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "email.resend.read-timeout은 auth.email-verification.code-ttl보다 짧아야 합니다."
                );
    }

    private ResendProperties properties(Duration connectTimeout, Duration readTimeout) {
        return new ResendProperties(
                "test-api-key",
                "no-reply@omagotchi.test",
                connectTimeout,
                readTimeout
        );
    }

    private EmailVerificationProperties verificationProperties(Duration codeTtl) {
        return new EmailVerificationProperties(
                codeTtl,
                Duration.ofMinutes(1),
                5,
                "test-hmac-secret-with-at-least-32-characters"
        );
    }
}
