package site.omagotchi.identityservice.global.logging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import site.omagotchi.identityservice.global.exception.ErrorCode;

import java.util.UUID;

/** HTTP 내부 오류의 중앙 검색용 이벤트와 로컬 진단 이벤트 기록. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpErrorEventLogger {

    private final Tracer tracer;

    public void log(
            Exception exception,
            ErrorCode errorCode,
            int statusCode,
            HttpServletRequest request
    ) {
        String eventId = UUID.randomUUID().toString();
        Span currentSpan = this.tracer.currentSpan();
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

        LoggingEventBuilder errorEvent = log.atError()
                .addKeyValue("event.id", eventId)
                .addKeyValue("event.dataset", "identity-service.error")
                .addKeyValue("event.action", "http.server.request.failed")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("error.code", errorCode.code())
                .addKeyValue("error.type", exception.getClass().getName())
                .addKeyValue("omagotchi.error.stack_trace", ErrorStackTrace.format(exception))
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("http.response.status_code", statusCode);
        if (route != null) {
            errorEvent = errorEvent.addKeyValue("omagotchi.http.route", route.toString());
        }
        errorEvent = addTraceFields(errorEvent, currentSpan);
        errorEvent.log("HTTP server error");

        LoggingEventBuilder diagnosticEvent = log.atError()
                .addKeyValue("event.id", eventId)
                .addKeyValue("event.dataset", "identity-service.diagnostic")
                .addKeyValue("event.action", "http.server.request.failed")
                .addKeyValue("event.outcome", "failure");
        diagnosticEvent = addTraceFields(diagnosticEvent, currentSpan);
        diagnosticEvent.setCause(exception).log("HTTP server failure diagnostic");
    }

    private static LoggingEventBuilder addTraceFields(LoggingEventBuilder event, Span span) {
        if (span == null) {
            return event;
        }
        return event
                .addKeyValue("trace.id", span.context().traceId())
                .addKeyValue("span.id", span.context().spanId());
    }
}
