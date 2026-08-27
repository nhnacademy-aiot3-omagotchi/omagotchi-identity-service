package site.omagotchi.identityservice.email.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.email-verification")
public record EmailVerificationProperties(

        @NotNull(message = "auth.email-verification.code-ttl은 필수입니다.")
        @DurationMin(
                seconds = 1,
                message = "auth.email-verification.code-ttl은 1초 이상이어야 합니다."
        )
        Duration codeTtl,

        @NotNull(message = "auth.email-verification.resend-cooldown은 필수입니다.")
        @DurationMin(
                seconds = 1,
                message = "auth.email-verification.resend-cooldown은 1초 이상이어야 합니다."
        )
        Duration resendCooldown,

        @Min(
                value = 1,
                message = "auth.email-verification.maximum-attempts는 1 이상이어야 합니다."
        )
        int maximumAttempts
) {
}
