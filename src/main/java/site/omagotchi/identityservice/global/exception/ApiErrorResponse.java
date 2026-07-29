package site.omagotchi.identityservice.global.exception;

/**
 * API 오류를 JSON으로 전달하는 응답.
 *
 * @param path 요청 URI. HTML에 직접 삽입하지 않는다.
 */
public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String path
) {
}
