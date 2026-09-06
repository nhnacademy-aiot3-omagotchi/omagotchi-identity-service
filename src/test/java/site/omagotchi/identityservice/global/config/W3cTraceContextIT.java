package site.omagotchi.identityservice.global.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import site.omagotchi.identityservice.global.requestid.RequestId;
import site.omagotchi.identityservice.global.requestid.RequestIdRestClientInterceptor;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

@SpringBootTest(
        classes = W3cTraceContextIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.name=w3c-trace-context-test",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "management.tracing.sampling.probability=1.0",
                "management.tracing.propagation.type=w3c",
                "management.tracing.baggage.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        }
)
class W3cTraceContextIT {

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private Tracer tracer;

    @Test
    @DisplayName("자동 구성 RestClient의 Request ID와 W3C traceparent 전파")
    void propagatesRequestIdAndTraceparent() {
        // Given
        MockRestServiceServer server = MockRestServiceServer.bindTo(this.restClientBuilder).build();
        RestClient client = this.restClientBuilder
                .baseUrl("http://trace.test")
                .requestInterceptor(new RequestIdRestClientInterceptor())
                .build();
        Span parent = this.tracer.nextSpan().name("w3c-propagation-test").start();
        server.expect(requestTo("http://trace.test/probe"))
                .andExpect(request -> {
                    then(request.getHeaders().getFirst("traceparent")).matches(
                            "^00-" + parent.context().traceId()
                                    + "-[0-9a-f]{16}-[0-9a-f]{2}$"
                    );
                    then(request.getHeaders().getFirst(RequestId.HEADER_NAME))
                            .matches("^[0-9a-f]{32}$");
                    then(request.getHeaders().getFirst("baggage")).isNull();
                })
                .andRespond(withNoContent());

        // When
        try (Tracer.SpanInScope ignored = this.tracer.withSpan(parent)) {
            client.get().uri("/probe").retrieve().toBodilessEntity();
        } finally {
            parent.end();
        }

        // Then
        server.verify();
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
