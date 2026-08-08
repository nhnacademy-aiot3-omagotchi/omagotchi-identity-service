package site.omagotchi.identityservice.global.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;

// 사용자 로그인 자격 증명과 분리된 Frontend 프로세스용 HTTP Basic Credential
@Validated
@ConfigurationProperties(prefix = "auth.frontend")
public record FrontendCredentialProperties(
        @NotBlank(message = "auth.frontend.username은 비어 있을 수 없습니다.")
        String username,

        @NotBlank(message = "auth.frontend.password는 비어 있을 수 없습니다.")
        String password
) {

    // HTTP Basic의 username:password 구분자 충돌 방지
    @AssertTrue(message = "auth.frontend.username에는 ':'를 사용할 수 없습니다.")
    public boolean isUsernameBasicCompatible() {
        return username == null || !username.contains(":");
    }

    // 예측하기 어려운 프로세스 Credential을 위한 최소 길이
    @AssertTrue(message = "auth.frontend.password는 32자 이상이어야 합니다.")
    public boolean isPasswordLengthValid() {
        return password == null || password.length() >= 32;
    }

    // BCrypt의 72바이트 이후 입력 절삭 방지
    @AssertTrue(message = "auth.frontend.password는 UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordByteLengthValid() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "FrontendCredentialProperties[username=" + username
                + ", password=[REDACTED]]";
    }
}
