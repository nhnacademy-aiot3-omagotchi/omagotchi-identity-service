package site.omagotchi.identityservice.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import site.omagotchi.identityservice.global.exception.ApiErrorResponse;
import site.omagotchi.identityservice.global.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Controller 이전의 Frontend HTTP Basic 실패를 공통 JSON 오류로 변환하는 Security 경계
// RestControllerAdvice 적용 전 단계로 인한 ServletResponse 직접 작성
@Component
@RequiredArgsConstructor
public class FrontendSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String BASIC_CHALLENGE =
            "Basic realm=\"omagotchi-identity-frontend\", charset=\"UTF-8\"";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException exception
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BASIC_CHALLENGE);
        write(response, request, SecurityErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AccessDeniedException exception
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        write(response, request, SecurityErrorCode.ACCESS_DENIED);
    }

    private void write(
            HttpServletResponse response,
            HttpServletRequest request,
            ErrorCode errorCode
    ) throws IOException {
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
