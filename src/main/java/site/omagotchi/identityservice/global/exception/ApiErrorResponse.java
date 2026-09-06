package site.omagotchi.identityservice.global.exception;

/**
 * API JSON 오류 응답
 *
 * @param path 요청 URI. HTML 직접 삽입 금지
 * @param requestId 요청 추적 ID
 */
public record ApiErrorResponse(
        String code,
        String message,
        String path,
        String requestId
) {
}
