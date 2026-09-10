package site.omagotchi.identityservice.global.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.identityservice.global.requestid.RequestId;
import site.omagotchi.identityservice.global.requestid.RequestIdFilter;
import site.omagotchi.identityservice.global.exception.GlobalExceptionHandler;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(
        classes = HttpObservabilityIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.name=http-observability-test",
                "server.address=127.0.0.1",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "management.tracing.sampling.probability=1.0",
                "management.tracing.propagation.type=w3c",
                "management.tracing.baggage.enabled=false",
                "logging.level.site.omagotchi.identityservice.global.logging.HttpAccessLogObservationHandler=INFO",
                "logging.structured.format.console=ecs",
                "logging.structured.ecs.service.environment=test",
                "logging.structured.json.exclude=traceId,spanId",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        }
)
@ExtendWith(OutputCaptureExtension.class)
class HttpObservabilityIT {

    private static final String REQUEST_ID = "0123456789abcdef0123456789abcdef";
    private static final String TRACE_ID = "11111111111111111111111111111111";
    private static final String TRACEPARENT =
            "00-" + TRACE_ID + "-2222222222222222-01";
    private static final String FAILURE_DETAIL = "local-diagnostic-detail";
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @Value("${local.server.port}")
    private int port;

    @Test
    @DisplayName("접근 이벤트의 Request ID·W3C Trace Context·Route 연결")
    void connectsRequestAndTraceIdentifiers(CapturedOutput output) throws Exception {
        // Given
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + this.port + "/probe/42"))
                .header(RequestId.HEADER_NAME, REQUEST_ID)
                .header("traceparent", TRACEPARENT)
                .GET()
                .build();

        // When
        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());

        // Then
        then(response.statusCode()).isEqualTo(204);
        JsonNode event = findEvent(output, "identity-service.http");
        then(event.at("/http/request/id").asString()).isEqualTo(REQUEST_ID);
        then(event.at("/http/response/status_code").asInt()).isEqualTo(204);
        then(event.at("/omagotchi/http/route").asString())
                .isEqualTo("/probe/{item-id}");
        then(event.at("/trace/id").asString()).isEqualTo(TRACE_ID);
        then(event.at("/span/id").asString()).matches("^[0-9a-f]{16}$");
        then(event.has("traceId")).isFalse();
        then(event.has("spanId")).isFalse();
    }

    @Test
    @DisplayName("500 오류의 안전 이벤트와 로컬 진단 이벤트 연결")
    void separatesSafeAndDiagnosticFailureEvents(CapturedOutput output) throws Exception {
        // Given
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + this.port + "/failure"))
                .header(RequestId.HEADER_NAME, REQUEST_ID)
                .header("traceparent", TRACEPARENT)
                .GET()
                .build();

        // When
        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());

        // Then
        then(response.statusCode()).isEqualTo(500);
        JsonNode errorEvent = findEvent(output, "identity-service.error");
        JsonNode diagnosticEvent = findEvent(output, "identity-service.diagnostic");
        then(errorEvent.at("/http/request/id").asString()).isEqualTo(REQUEST_ID);
        then(errorEvent.at("/trace/id").asString()).isEqualTo(TRACE_ID);
        then(errorEvent.at("/error/code").asString())
                .isEqualTo("COMMON_INTERNAL_SERVER_ERROR");
        then(errorEvent.at("/error/type").asString())
                .isEqualTo(IllegalStateException.class.getName());
        then(errorEvent.at("/omagotchi/error/stack_trace").asString())
                .contains(IllegalStateException.class.getName()).doesNotContain(FAILURE_DETAIL);
        then(errorEvent.at("/error/stack_trace").isMissingNode()).isTrue();
        then(errorEvent.toString()).doesNotContain(FAILURE_DETAIL);
        then(diagnosticEvent.at("/event/id").asString())
                .isEqualTo(errorEvent.at("/event/id").asString());
        then(diagnosticEvent.at("/error/message").asString()).isEqualTo(FAILURE_DETAIL);
        then(diagnosticEvent.at("/error/stack_trace").asString()).contains(FAILURE_DETAIL);
    }

    private static JsonNode findEvent(CapturedOutput output, String dataset) {
        List<JsonNode> events = output.getAll().lines()
                .map(String::trim)
                .filter(line -> line.startsWith("{"))
                .map(HttpObservabilityIT::readJson)
                .filter(json -> dataset.equals(json.at("/event/dataset").asString()))
                .toList();
        then(events).singleElement();
        return events.getFirst();
    }

    private static JsonNode readJson(String line) {
        try {
            return JSON.readTree(line);
        } catch (Exception exception) {
            throw new AssertionError("구조화 로그 JSON 해석 실패", exception);
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({
            ProbeController.class,
            TestSecurityConfiguration.class,
            RequestIdFilter.class,
            HttpAccessLogObservationHandler.class,
            HttpErrorEventLogger.class,
            GlobalExceptionHandler.class
    })
    static class TestApplication {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/{item-id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void probe() {
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException(FAILURE_DETAIL);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }
    }
}
