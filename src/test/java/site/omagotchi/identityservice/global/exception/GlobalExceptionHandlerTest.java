package site.omagotchi.identityservice.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import site.omagotchi.identityservice.emailverification.application.EmailDeliveryException;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationCooldownException;
import site.omagotchi.identityservice.emailverification.application.EmailVerificationErrorCode;

import static org.assertj.core.api.BDDAssertions.then;

class GlobalExceptionHandlerTest {

    private static final String REQUEST_URI = "/test";
    private static final String DIAGNOSTIC_MESSAGE =
            "operation = update, expectedStatus = ACTIVE, actualStatus = CLOSED";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("호출 계약 위반을 내부 오류로 숨김")
    void hidesIllegalArgumentException() {
        // Given
        MockHttpServletRequest request = requestForTest();
        IllegalArgumentException exception =
                new IllegalArgumentException("외부에 노출하면 안 되는 인자 정보");

        // When
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpectedException(exception, request);

        // Then
        thenUnexpectedExceptionIsHidden(response);
    }

    @Test
    @DisplayName("내부 상태 위반을 내부 오류로 숨김")
    void hidesIllegalStateException() {
        // Given
        MockHttpServletRequest request = requestForTest();
        IllegalStateException exception =
                new IllegalStateException("외부에 노출하면 안 되는 상태 정보");

        // When
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpectedException(exception, request);

        // Then
        thenUnexpectedExceptionIsHidden(response);
    }

    @Test
    @DisplayName("외부 응답에서 진단 메시지 숨김")
    void hidesDiagnosticMessageFromResponse() {
        // Given
        MockHttpServletRequest request = requestForTest();
        BusinessException exception = new BusinessException(
                CommonErrorCode.INVALID_REQUEST,
                DIAGNOSTIC_MESSAGE
        );

        // When
        ResponseEntity<ApiErrorResponse> response =
                handler.handleBusinessException(exception, request);

        // Then
        then(response.getBody().message())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST.message());
    }

    @Test
    @DisplayName("쿨다운 오류에 Retry-After 초 단위 Header 포함")
    void addsRetryAfterHeader() {
        // Given
        EmailVerificationCooldownException exception =
                new EmailVerificationCooldownException(42);

        // When
        ResponseEntity<ApiErrorResponse> response = handler.handleBusinessException(
                exception,
                requestForTest()
        );

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        then(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
        then(response.getBody().code())
                .isEqualTo(EmailVerificationErrorCode.COOLDOWN_ACTIVE.code());
    }

    @Test
    @DisplayName("외부 메일 장애를 원인 노출 없이 503으로 변환")
    void mapsDependencyUnavailableException() {
        // Given
        DependencyUnavailableException exception = new DependencyUnavailableException(
                EmailVerificationErrorCode.DELIVERY_UNAVAILABLE,
                new EmailDeliveryException("sensitive provider detail", new RuntimeException())
        );

        // When
        ResponseEntity<ApiErrorResponse> response = handler.handleDependencyUnavailable(
                exception,
                requestForTest()
        );

        // Then
        then(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        then(response.getBody().message())
                .isEqualTo(EmailVerificationErrorCode.DELIVERY_UNAVAILABLE.message());
    }

    private MockHttpServletRequest requestForTest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI(REQUEST_URI);
        return request;
    }

    private void thenUnexpectedExceptionIsHidden(ResponseEntity<ApiErrorResponse> response) {
        then(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        then(response.getBody()).isEqualTo(new ApiErrorResponse(
                CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                CommonErrorCode.INTERNAL_SERVER_ERROR.message(),
                REQUEST_URI,
                null
        ));
    }
}
