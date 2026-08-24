package site.omagotchi.identityservice.global.security.frontend;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 사용자 로그인 자격 증명과 분리된 Frontend 프로세스용 HTTP Basic Credential
@Validated
@ConfigurationProperties(prefix = "auth.frontend")
public record FrontendCredentialProperties(
        @NotBlank(message = "auth.frontend.username은 비어 있을 수 없습니다.")
        @Pattern(
                regexp = "^[^:]*$",
                message = "auth.frontend.username에는 ':'를 사용할 수 없습니다."
        )
        String username,

        @NotBlank(message = "auth.frontend.password는 비어 있을 수 없습니다.")
        String password
) {

    // 거부된 Credential 원문을 Binding 오류의 rejected value에 남기지 않는 파생값 검증
    @AssertTrue(message = "auth.frontend.password는 32자 이상 72자 이하여야 합니다.")
    public boolean isPasswordLengthValid() {
        return password == null || password.length() >= 32 && password.length() <= 72;
    }

    // 환경 변수와 HTTP Basic 전송에 안전한 ASCII 난수 문자 범위
    @AssertTrue(message = "auth.frontend.password는 영문자·숫자·'-'·'_'만 사용할 수 있습니다.")
    public boolean isPasswordCharacterSetValid() {
        return password == null || password.matches("[A-Za-z0-9_-]+");
    }

    @Override
    public String toString() {
        return "FrontendCredentialProperties[username=" + username
                + ", password=[REDACTED]]";
    }
}
