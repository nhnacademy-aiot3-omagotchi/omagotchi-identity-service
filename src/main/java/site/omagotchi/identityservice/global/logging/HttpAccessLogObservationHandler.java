package site.omagotchi.identityservice.global.logging;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.global.requestid.RequestId;
import site.omagotchi.identityservice.global.requestid.RequestIdContext;

/** Spring MVC HTTP Observation 종료 시점의 요청별 접근 이벤트 기록. */
@Slf4j
@Component
public class HttpAccessLogObservationHandler implements ObservationHandler<ServerRequestObservationContext> {

    private static final String STARTED_AT_NANOS =
            HttpAccessLogObservationHandler.class.getName() + ".startedAtNanos";

    @Override
    public void onStart(ServerRequestObservationContext context) {
        context.put(STARTED_AT_NANOS, System.nanoTime());
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        HttpServletRequest request = context.getCarrier();
        HttpServletResponse response = context.getResponse();
        if (request == null || response == null) {
            return;
        }

        String path = request.getRequestURI();
        // 주기적인 생존 확인·메트릭 수집의 접근 이벤트 제외
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")
                || path.equals("/actuator/prometheus")) {
            return;
        }

        int statusCode = context.getError() != null && !response.isCommitted()
                ? 500
                : response.getStatus();
        Long startedAtNanos = context.get(STARTED_AT_NANOS);
        long duration = startedAtNanos == null
                ? 0L
                : Math.max(0L, System.nanoTime() - startedAtNanos);
        LoggingEventBuilder event = statusCode >= 500 ? log.atError() : log.atInfo();

        event = event.addKeyValue("event.dataset", "identity-service.http")
                .addKeyValue("event.action", "http.server.request.completed")
                .addKeyValue("event.outcome", context.getError() != null || statusCode >= 400
                        ? "failure"
                        : "success")
                .addKeyValue("event.duration", duration)
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("http.response.status_code", statusCode);

        if (context.getPathPattern() != null) {
            event = event.addKeyValue("omagotchi.http.route", context.getPathPattern());
        }
        if (context.getError() != null) {
            event = event.addKeyValue("error.type", context.getError().getClass().getName());
        }

        TracingObservationHandler.TracingContext tracingContext =
                context.get(TracingObservationHandler.TracingContext.class);
        Span span = tracingContext == null ? null : tracingContext.getSpan();
        if (span != null) {
            event = event
                    .addKeyValue("trace.id", span.context().traceId())
                    .addKeyValue("span.id", span.context().spanId());
        }
        // 비동기 완료 시 최초 Filter의 MDC가 없으므로 요청에 보관한 ID 사용
        // 로그 호출 구간에만 적용하고 완료 스레드의 이전 MDC 복원
        Object storedRequestId = request.getAttribute(RequestId.ATTRIBUTE_NAME);
        try (RequestIdContext.Scope ignored = RequestIdContext.openScope(
                storedRequestId instanceof RequestId requestId ? requestId : null
        )) {
            event.log("HTTP request completed");
        }
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }
}
