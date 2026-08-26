package site.omagotchi.identityservice.email.infrastructure.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "email.resend")
public record ResendProperties(
        @NotBlank(message = "email.resend.api-key는 비어 있을 수 없습니다.")
        String apiKey,

        @NotBlank(message = "email.resend.from-email은 비어 있을 수 없습니다.")
        @Email(message = "email.resend.from-email의 이메일 형식이 올바르지 않습니다.")
        String fromEmail
) {

    @Override
    public String toString() {
        return "ResendProperties[apiKey=[REDACTED], fromEmail=" + fromEmail + "]";
    }
}
