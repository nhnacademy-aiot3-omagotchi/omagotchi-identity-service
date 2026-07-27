package site.omagotchi.identityservice.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.global.exception.ApiErrorResponse;
import site.omagotchi.identityservice.global.exception.ErrorHttpStatusMapper;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Controller 이전에 발생한 Security 예외를 공통 JSON 응답으로 변환
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException exception
    ) throws IOException {
        write(response, request, SecurityErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AccessDeniedException exception
    ) throws IOException {
        write(response, request, SecurityErrorCode.ACCESS_DENIED);
    }

    private void write(
            HttpServletResponse response,
            HttpServletRequest request,
            SecurityErrorCode errorCode
    ) throws IOException {
        int status = ErrorHttpStatusMapper.map(errorCode.type()).value();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(status, errorCode.code(), errorCode.message(), request.getRequestURI())
        );
    }
}
