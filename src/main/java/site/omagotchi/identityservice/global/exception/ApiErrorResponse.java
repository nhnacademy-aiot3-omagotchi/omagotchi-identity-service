package site.omagotchi.identityservice.global.exception;

public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String path
) {
}
