package site.omagotchi.identityservice.global.requestid;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/** Request ID의 MDC 범위 관리. */
public final class RequestIdContext {

    public static final String MDC_KEY = "http.request.id";

    private RequestIdContext() {
    }

    /** 신규 Request ID 범위 생성과 이전 값 복원. */
    public static Scope openNew() {
        return openScope(RequestId.generate());
    }

    /** 지정 Request ID의 임시 적용과 이전 MDC 복원. 값 부재 시 범위 안의 ID 제거. */
    public static Scope openScope(@Nullable RequestId requestId) {
        String previousRequestId = MDC.get(MDC_KEY);
        if (requestId == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, requestId.value());
        }
        return () -> {
            if (previousRequestId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousRequestId);
            }
        };
    }

    /** HTTP 요청 범위 생성과 종료 시 값 제거. */
    public static Scope openInbound(RequestId requestId) {
        MDC.put(MDC_KEY, requestId.value());
        return () -> MDC.remove(MDC_KEY);
    }

    public static RequestId currentOrGenerate() {
        String current = MDC.get(MDC_KEY);
        return RequestId.isValid(current) ? new RequestId(current) : RequestId.generate();
    }

    public static @Nullable String currentValue() {
        return MDC.get(MDC_KEY);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
