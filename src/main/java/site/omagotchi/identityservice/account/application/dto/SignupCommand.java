package site.omagotchi.identityservice.account.application.dto;

public record SignupCommand(
        String email,
        String password,
        String name
) {
}
