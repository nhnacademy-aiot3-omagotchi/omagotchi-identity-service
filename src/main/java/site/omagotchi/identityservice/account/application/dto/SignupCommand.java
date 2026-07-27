package site.omagotchi.identityservice.account.application.dto;

public record SignupCommand(
        String email,
        String password,
        String name
) {

    // 민감정보의 로그 노출 방지
    @Override
    public String toString() {
        return "SignupCommand[sensitive fields redacted]";
    }
}
