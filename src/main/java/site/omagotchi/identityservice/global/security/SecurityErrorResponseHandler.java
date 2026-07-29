package site.omagotchi.identityservice.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
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

// Controller 이전에 발생한 Security 예외를 공통 JSON 응답으로 변환
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint =
            new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerTokenAccessDeniedHandler =
            new BearerTokenAccessDeniedHandler();

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
        // Bearer delegate가 RFC 6750 오류에 맞춰 정한 상태와 Header를 보존한다.
        int status = response.getStatus();
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(status, errorCode.code(), errorCode.message(), request.getRequestURI())
        );
    }
}
