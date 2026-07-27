package site.omagotchi.identityservice.account.application.dto;

public record SignupCommand(
        String email,
        String password,
        String name
) {

    // 인증 정보와 개인정보가 의도치 않게 로그로 노출되는 것을 막는 방어 코드
    @Override
    public String toString() {
        return "SignupCommand[sensitive fields redacted]";
    }
}
