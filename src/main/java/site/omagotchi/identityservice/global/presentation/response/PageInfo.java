package site.omagotchi.identityservice.global.presentation.response;

public record PageInfo(
        int number,
        int size,
        long totalElements,
        int totalPages
) {
}
