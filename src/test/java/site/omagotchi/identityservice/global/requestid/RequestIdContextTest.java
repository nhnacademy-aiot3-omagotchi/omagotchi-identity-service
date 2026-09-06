package site.omagotchi.identityservice.global.requestid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import static org.assertj.core.api.BDDAssertions.then;

class RequestIdContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("새 작업 범위의 Request ID 생성과 바깥 값 복원")
    void createsRequestIdAndRestoresOuterContext() {
        // Given
        String outerRequestId = "0123456789abcdef0123456789abcdef";
        MDC.put(RequestIdContext.MDC_KEY, outerRequestId);

        // When
        try (RequestIdContext.Scope ignored = RequestIdContext.openNew()) {
            // Then
            then(RequestIdContext.currentValue())
                    .matches("^[0-9a-f]{32}$")
                    .isNotEqualTo(outerRequestId);
        }

        then(RequestIdContext.currentValue()).isEqualTo(outerRequestId);
    }

    @Test
    @DisplayName("HTTP 요청 범위 종료 후 Request ID 제거")
    void removesInboundRequestIdAfterScopeCloses() {
        // Given
        RequestId requestId = new RequestId("abcdef0123456789abcdef0123456789");

        // When
        try (RequestIdContext.Scope ignored = RequestIdContext.openInbound(requestId)) {
            // Then
            then(RequestIdContext.currentValue()).isEqualTo(requestId.value());
        }

        then(RequestIdContext.currentValue()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "abcdef0123456789abcdef0123456789")
    @DisplayName("기존 Request ID의 임시 적용과 범위 종료 후 이전 MDC 복원")
    void restoresPreviousContextAfterScopedRequestId(String previousRequestId) {
        // Given: 이전 값이 있거나 없는 완료 스레드
        if (previousRequestId != null) {
            MDC.put(RequestIdContext.MDC_KEY, previousRequestId);
        }
        RequestId requestId = new RequestId("0123456789abcdef0123456789abcdef");

        // When: 최초 요청의 ID를 로그 호출 범위에만 적용
        try (RequestIdContext.Scope ignored = RequestIdContext.openScope(requestId)) {
            // Then: 새 값 생성 없이 최초 ID 사용
            then(RequestIdContext.currentValue()).isEqualTo(requestId.value());
        }
        then(RequestIdContext.currentValue()).isEqualTo(previousRequestId);
    }

    @Test
    @DisplayName("요청 ID가 없는 로그 범위의 다른 요청 ID 차단과 이전 MDC 복원")
    void clearsUnrelatedRequestIdWithinEmptyScope() {
        // Given
        String previousRequestId = "abcdef0123456789abcdef0123456789";
        MDC.put(RequestIdContext.MDC_KEY, previousRequestId);

        // When
        try (RequestIdContext.Scope ignored = RequestIdContext.openScope(null)) {
            // Then
            then(RequestIdContext.currentValue()).isNull();
        }
        then(RequestIdContext.currentValue()).isEqualTo(previousRequestId);
    }
}
