package site.omagotchi.identityservice.global.requestid;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0123456789abcdef0123456789abcdef", "Dev-Request_01.test", "Z"})
    @DisplayName("허용한 단일 Request ID의 요청·응답·MDC 전파")
    void propagatesOneValidRequestId(String incoming) throws Exception {
        // Given
        givenRequestIds(incoming);
        doAnswer(invocation -> {
            then(RequestIdContext.currentValue()).isEqualTo(incoming);
            return null;
        }).when(this.filterChain).doFilter(this.request, this.response);

        // When
        this.filter.doFilter(this.request, this.response, this.filterChain);

        // Then
        verify(this.response).setHeader(RequestId.HEADER_NAME, incoming);
        verify(this.filterChain).doFilter(this.request, this.response);
        then(RequestIdContext.currentValue()).isNull();
    }

    @Test
    @DisplayName("Request ID 헤더 누락 시 신규 값 발급")
    void generatesRequestIdWhenHeaderMissing() throws Exception {
        // Given
        givenRequestIds();

        // When
        this.filter.doFilter(this.request, this.response, this.filterChain);

        // Then
        verify(this.response).setHeader(
                eq(RequestId.HEADER_NAME),
                argThat(value -> value.matches("[0-9a-f]{32}"))
        );
    }

    @Test
    @DisplayName("중복 Request ID의 신규 단일 값 교체")
    void replacesDuplicateRequestIds() throws Exception {
        // Given
        String first = "0123456789abcdef0123456789abcdef";
        String second = "abcdef0123456789abcdef0123456789";
        givenRequestIds(first, second);

        // When
        this.filter.doFilter(this.request, this.response, this.filterChain);

        // Then
        verify(this.response).setHeader(
                eq(RequestId.HEADER_NAME),
                argThat(value -> value.matches("[0-9a-f]{32}")
                        && !first.equals(value)
                        && !second.equals(value))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid request id",
            "",
            " ",
            "0123456789abcdef0123456789abcdef!",
            "first,second",
            "한글",
            "0123456789abcdef\r\nX-Injected: 1"
    })
    @DisplayName("잘못된 Request ID의 신규 값 교체")
    void replacesInvalidRequestId(String invalidRequestId) throws Exception {
        // Given
        givenRequestIds(invalidRequestId);

        // When
        this.filter.doFilter(this.request, this.response, this.filterChain);

        // Then
        verify(this.response).setHeader(
                eq(RequestId.HEADER_NAME),
                argThat(value -> value.matches("[0-9a-f]{32}") && !invalidRequestId.equals(value))
        );
    }

    @Test
    @DisplayName("후속 처리 예외 시 MDC 정리")
    void clearsMdcWhenChainThrows() throws IOException, ServletException {
        // Given
        givenRequestIds();
        doThrow(new ServletException("test failure"))
                .when(this.filterChain)
                .doFilter(this.request, this.response);

        // When
        assertThrows(
                ServletException.class,
                () -> this.filter.doFilter(this.request, this.response, this.filterChain)
        );

        // Then
        then(RequestIdContext.currentValue()).isNull();
    }

    @Test
    @DisplayName("긴 Request ID의 앞 32자를 요청·응답·MDC에 동일하게 적용")
    void propagatesTruncatedRequestId() throws Exception {
        // Given
        String incoming = "Dev-Request_0123456789.abcdefghijk-extra";
        String expected = incoming.substring(0, 32);
        givenRequestIds(incoming);
        doAnswer(invocation -> {
            then(RequestIdContext.currentValue()).isEqualTo(expected);
            return null;
        }).when(this.filterChain).doFilter(this.request, this.response);

        // When
        this.filter.doFilter(this.request, this.response, this.filterChain);

        // Then
        verify(this.response).setHeader(RequestId.HEADER_NAME, expected);
        verify(this.request).setAttribute(RequestId.ATTRIBUTE_NAME, new RequestId(expected));
        then(RequestIdContext.currentValue()).isNull();
    }

    private void givenRequestIds(String... requestIds) {
        when(this.request.getHeaders(RequestId.HEADER_NAME))
                .thenReturn(Collections.enumeration(List.of(requestIds)));
    }
}
