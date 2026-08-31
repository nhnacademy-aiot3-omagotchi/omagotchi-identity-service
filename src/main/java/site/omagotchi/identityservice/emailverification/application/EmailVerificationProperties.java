package site.omagotchi.identityservice.emailverification.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.email-verification")
public record EmailVerificationProperties(

        @NotNull(message = "auth.email-verification.code-ttl은 필수입니다.")
        @DurationMin(seconds = 1, message = "auth.email-verification.code-ttl은 1초 이상이어야 합니다.")
        Duration codeTtl,

        @NotNull(message = "auth.email-verification.cooldown은 필수입니다.")
        @DurationMin(seconds = 1, message = "auth.email-verification.cooldown은 1초 이상이어야 합니다.")
        Duration cooldown,

        @Min(value = 1, message = "auth.email-verification.maximum-failed-attempts는 1 이상이어야 합니다.")
        @Max(
                value = Short.MAX_VALUE,
                message = "auth.email-verification.maximum-failed-attempts가 너무 높습니다."
        )
        int maximumFailedAttempts,

        @NotBlank(message = "auth.email-verification.hmac-secret은 필수입니다.")
        @Size(min = 32, message = "auth.email-verification.hmac-secret은 32자 이상이어야 합니다.")
        String hmacSecret
) {
}
