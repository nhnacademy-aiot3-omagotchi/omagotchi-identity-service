package site.omagotchi.identityservice.global.requestid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

class RequestIdRestClientInterceptorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("현재 요청의 Request ID 전파")
    void propagatesCurrentRequestId() {
        // Given
        String requestId = "Dev-Request_01.test";
        MDC.put(RequestIdContext.MDC_KEY, requestId);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder
                .requestInterceptor(new RequestIdRestClientInterceptor())
                .build();
        server.expect(requestTo("http://downstream.test/probe"))
                .andExpect(header(RequestId.HEADER_NAME, requestId))
                .andRespond(withNoContent());

        // When
        client.get()
                .uri("http://downstream.test/probe")
                .retrieve()
                .toBodilessEntity();

        // Then
        server.verify();
    }

    @Test
    @DisplayName("잘못된 MDC 값의 신규 Request ID 교체")
    void replacesInvalidMdcValue() {
        // Given
        MDC.put(RequestIdContext.MDC_KEY, "invalid request id");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder
                .requestInterceptor(new RequestIdRestClientInterceptor())
                .build();
        server.expect(requestTo("http://downstream.test/probe"))
                .andExpect(header(
                        RequestId.HEADER_NAME,
                        matchesPattern("^[0-9a-f]{32}$")
                ))
                .andRespond(withNoContent());

        // When
        client.get()
                .uri("http://downstream.test/probe")
                .retrieve()
                .toBodilessEntity();

        // Then
        server.verify();
    }
}
