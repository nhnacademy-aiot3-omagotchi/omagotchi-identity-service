package site.omagotchi.identityservice.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ServerHttpObservationFilter;
import site.omagotchi.identityservice.global.requestid.RequestId;
import site.omagotchi.identityservice.global.requestid.RequestIdContext;
import site.omagotchi.identityservice.global.requestid.RequestIdFilter;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;

class HttpAccessLogObservationHandlerTest {

    private static final String REQUEST_ID = "0123456789abcdef0123456789abcdef";
    private final HttpAccessLogObservationHandler handler =
            new HttpAccessLogObservationHandler();
    private final Logger logger =
            (Logger) LoggerFactory.getLogger(HttpAccessLogObservationHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            // Scope 종료 전 실제 로그 호출 시점의 MDC 보관
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private Level previousLevel;
    private boolean previousAdditive;

    @BeforeEach
    void attachLogCapture() {
        previousLevel = logger.getLevel();
        previousAdditive = logger.isAdditive();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
    }

    @AfterEach
    void restoreLoggingState() {
        logger.setLevel(previousLevel);
        logger.setAdditive(previousAdditive);
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    @DisplayName("완료 이벤트의 Request ID·Route·HTTP 결과 기록")
    void recordsCompletedRequestWithBoundedRoute() {
        // Given
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/items/sensitive-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute(RequestId.ATTRIBUTE_NAME, new RequestId(REQUEST_ID));
        response.setStatus(204);
        ServerRequestObservationContext context =
                new ServerRequestObservationContext(request, response);
        context.setPathPattern("/api/v1/items/{item-id}");

        // When
        this.handler.onStart(context);
        this.handler.onStop(context);

        // Then
        then(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        then(event.getLevel()).isEqualTo(Level.INFO);
        then(event.getMDCPropertyMap()).containsEntry(RequestIdContext.MDC_KEY, REQUEST_ID);
        then(fields(event))
                .containsEntry("event.dataset", "identity-service.http")
                .containsEntry("event.action", "http.server.request.completed")
                .containsEntry("event.outcome", "success")
                .containsEntry("http.request.method", "GET")
                .containsEntry("http.response.status_code", 204)
                .containsEntry("omagotchi.http.route", "/api/v1/items/{item-id}")
                .doesNotContainKey("url.path");
    }

    @Test
    @DisplayName("처리되지 않은 예외의 메시지·스택 제외와 유형 기록")
    void recordsOnlySafeErrorFields() {
        // Given
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/items/private-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServerRequestObservationContext context =
                new ServerRequestObservationContext(request, response);
        context.setError(new IllegalStateException("must-not-appear"));

        // When
        this.handler.onStart(context);
        this.handler.onStop(context);

        // Then
        then(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        then(event.getLevel()).isEqualTo(Level.ERROR);
        then(event.getFormattedMessage()).doesNotContain("must-not-appear");
        then(event.getThrowableProxy()).isNull();
        then(fields(event))
                .containsEntry("event.outcome", "failure")
                .containsEntry("http.response.status_code", 500)
                .containsEntry("error.type", IllegalStateException.class.getName());
    }

    @Test
    @DisplayName("생존 확인 접근 이벤트 제외")
    void skipsHealthCheck() {
        // Given
        ServerRequestObservationContext context = new ServerRequestObservationContext(
                new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse()
        );

        // When
        this.handler.onStart(context);
        this.handler.onStop(context);

        // Then
        then(appender.list).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("동기·비동기 실제 Filter 완료의 Request ID·최종 상태·접근 이벤트 1회 기록")
    void recordsOnceAtFilterCompletion(boolean async) throws Exception {
        // Given: 실제 Spring Observation Filter와 공통 Request ID Filter
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(this.handler);
        ServerHttpObservationFilter observationFilter = new ServerHttpObservationFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/stream");
        request.setAsyncSupported(true);
        request.addHeader(RequestId.HEADER_NAME, REQUEST_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When: 동기 완료 또는 첫 비동기 Dispatch 종료
        new RequestIdFilter().doFilter(request, response, (incoming, outgoing) ->
                observationFilter.doFilter(incoming, outgoing, (ignoredRequest, ignoredResponse) -> {
                    if (async) {
                        request.startAsync(request, response);
                    } else {
                        response.setStatus(204);
                    }
                })
        );
        then(RequestIdContext.currentValue()).isNull();
        if (async) {
            then(appender.list).isEmpty();
            response.setStatus(204);
            ((MockAsyncContext) request.getAsyncContext()).complete();
        }

        // Then: 최초 요청의 ID로 완료 로그 1회 기록과 MDC 정리
        then(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        then(event.getMDCPropertyMap()).containsEntry(RequestIdContext.MDC_KEY, REQUEST_ID);
        then(fields(event))
                .containsEntry("http.response.status_code", 204)
                .doesNotContainKey(RequestIdContext.MDC_KEY);
        then((Long) fields(event).get("event.duration")).isPositive();
        then(response.getHeader(RequestId.HEADER_NAME)).isEqualTo(REQUEST_ID);
        then(RequestIdContext.currentValue()).isNull();
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
