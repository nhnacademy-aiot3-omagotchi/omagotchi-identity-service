package site.omagotchi.identityservice.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.BDDAssertions.then;

class SecurityErrorResponseHandlerTest {

    @Test
    @DisplayName("Bearer 잘못된 요청의 400 상태와 Header 및 공통 오류 Code 유지")
    void preservesBearerInvalidRequestStatus() throws Exception {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        SecurityErrorResponseHandler handler = new SecurityErrorResponseHandler(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/users/me"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                BearerTokenErrors.invalidRequest("잘못된 Bearer 요청")
        );

        // When
        handler.commence(request, response, exception);

        // Then
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        then(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        then(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer")
                .contains("error=\"invalid_request\"");
        then(body.get("code").asString()).isEqualTo("COMMON_INVALID_REQUEST");
    }

    @Test
    @DisplayName("Bearer 접근 거부의 403 상태와 Header 및 공통 오류 Code 유지")
    void writesBearerAccessDeniedResponse() throws Exception {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        SecurityErrorResponseHandler handler = new SecurityErrorResponseHandler(objectMapper);

        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1")
                .build();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin");
        request.setUserPrincipal(new JwtAuthenticationToken(jwt));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        handler.handle(
                request,
                response,
                new AccessDeniedException("접근 거부")
        );

        // Then
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        then(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        then(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer")
                .contains("error=\"insufficient_scope\"");
        then(body.get("code").asString()).isEqualTo("AUTH_ACCESS_DENIED");
        then(body.get("path").asString()).isEqualTo("/api/v1/admin");
    }
}
