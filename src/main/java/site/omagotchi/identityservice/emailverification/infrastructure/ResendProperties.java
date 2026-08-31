package site.omagotchi.identityservice.emailverification.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "email.resend")
public record ResendProperties(
        @NotBlank(message = "email.resend.api-key는 필수입니다.")
        String apiKey,

        @NotBlank(message = "email.resend.from-email은 필수입니다.")
        String fromEmail,

        @NotNull(message = "email.resend.connect-timeout은 필수입니다.")
        @DurationMin(
                millis = 1,
                message = "email.resend.connect-timeout은 1ms 이상이어야 합니다."
        )
        Duration connectTimeout,

        @NotNull(message = "email.resend.read-timeout은 필수입니다.")
        @DurationMin(
                millis = 1,
                message = "email.resend.read-timeout은 1ms 이상이어야 합니다."
        )
        Duration readTimeout
) {

    @Override
    public String toString() {
        return "ResendProperties[apiKey=[REDACTED]"
                + ", fromEmail=" + fromEmail
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ']';
    }
}
