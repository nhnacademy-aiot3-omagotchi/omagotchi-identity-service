package site.omagotchi.identityservice.global.security.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.global.exception.ApiErrorResponse;
import site.omagotchi.identityservice.global.exception.CommonErrorCode;
import site.omagotchi.identityservice.global.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Controller 이전의 Bearer·HTTP Basic 인증 실패를 공통 JSON 오류로 변환하는 Security 경계
// 인증 방식별 Challenge Header 보존과 RestControllerAdvice 적용 전 ServletResponse 직접 작성
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint =
            new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerTokenAccessDeniedHandler =
            new BearerTokenAccessDeniedHandler();

    public AuthenticationEntryPoint basicAuthenticationEntryPoint(String realm) {
        String challenge = "Basic realm=\"" + realm + "\", charset=\"UTF-8\"";
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
            write(response, request, SecurityErrorCode.AUTHENTICATION_REQUIRED);
        };
    }

    public AccessDeniedHandler basicAccessDeniedHandler() {
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            write(response, request, SecurityErrorCode.ACCESS_DENIED);
        };
    }

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException exception
    ) throws IOException {
        bearerTokenAuthenticationEntryPoint.commence(request, response, exception);
        ErrorCode errorCode = response.getStatus() == HttpServletResponse.SC_BAD_REQUEST
                ? CommonErrorCode.INVALID_REQUEST
                : SecurityErrorCode.AUTHENTICATION_REQUIRED;
        write(response, request, errorCode);
    }

    @Override
    public void handle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AccessDeniedException exception
    ) throws IOException {
        bearerTokenAccessDeniedHandler.handle(request, response, exception);
        write(response, request, SecurityErrorCode.ACCESS_DENIED);
    }

    private void write(
            HttpServletResponse response,
            HttpServletRequest request,
            ErrorCode errorCode
    ) throws IOException {
        // 호출한 인증 방식별 Handler가 결정한 HTTP 상태와 Header 보존
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(
                        errorCode.code(),
                        errorCode.message(),
                        request.getRequestURI(),
                        null
                )
        );
    }
}
