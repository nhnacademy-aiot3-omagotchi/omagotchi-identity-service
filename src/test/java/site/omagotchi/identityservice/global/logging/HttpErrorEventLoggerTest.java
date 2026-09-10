package site.omagotchi.identityservice.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import site.omagotchi.identityservice.global.exception.CommonErrorCode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpErrorEventLoggerTest {

    private static final String TRACE_ID = "11111111111111111111111111111111";
    private static final String SPAN_ID = "2222222222222222";

    @Test
    @DisplayName("안전한 검색 이벤트와 같은 식별자의 진단 이벤트 분리")
    void separatesSafeAndDiagnosticEvents() {
        // Given
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(TRACE_ID);
        when(traceContext.spanId()).thenReturn(SPAN_ID);
        HttpErrorEventLogger eventLogger = new HttpErrorEventLogger(tracer);
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/items/private-value");
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/items/{item-id}"
        );
        IllegalStateException exception =
                new IllegalStateException("local-diagnostic-detail");

        // When
        List<ILoggingEvent> events = recordEvents(() -> eventLogger.log(
                exception,
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                500,
                request
        ));

        // Then
        then(events).hasSize(2);
        Map<String, Object> safeFields = fields(events.getFirst());
        Map<String, Object> diagnosticFields = fields(events.getLast());
        then(safeFields)
                .containsEntry("event.dataset", "identity-service.error")
                .containsEntry("error.code", CommonErrorCode.INTERNAL_SERVER_ERROR.code())
                .containsEntry("error.type", IllegalStateException.class.getName())
                .containsEntry("omagotchi.http.route", "/api/v1/items/{item-id}")
                .containsEntry("trace.id", TRACE_ID)
                .containsEntry("span.id", SPAN_ID);
        then(events.getFirst().getFormattedMessage())
                .doesNotContain("local-diagnostic-detail");
        then(events.getFirst().getThrowableProxy()).isNull();
        then((String) safeFields.get("omagotchi.error.stack_trace"))
                .contains("HttpErrorEventLoggerTest.separatesSafeAndDiagnosticEvents(")
                .doesNotContain("local-diagnostic-detail");

        then(diagnosticFields)
                .containsEntry("event.dataset", "identity-service.diagnostic")
                .containsEntry("event.id", safeFields.get("event.id"))
                .containsEntry("trace.id", TRACE_ID)
                .containsEntry("span.id", SPAN_ID);
        then(events.getLast().getThrowableProxy().getMessage())
                .isEqualTo("local-diagnostic-detail");
    }

    private static List<ILoggingEvent> recordEvents(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpErrorEventLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        boolean additive = logger.isAdditive();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);

        try {
            action.run();
            return List.copyOf(appender.list);
        } finally {
            logger.setAdditive(additive);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
