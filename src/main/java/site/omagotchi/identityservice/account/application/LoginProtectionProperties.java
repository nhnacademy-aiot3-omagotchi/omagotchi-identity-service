package site.omagotchi.identityservice.account.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.login-protection")
public record LoginProtectionProperties(

        @Min(value = 1, message = "auth.login-protection.maximum-failed-attempts는 1 이상이어야 합니다.")
        @Max(
                value = Short.MAX_VALUE,
                message = "auth.login-protection.maximum-failed-attempts는 32767 이하여야 합니다."
        )
        int maximumFailedAttempts,

        @NotNull(message = "auth.login-protection.lock-duration은 필수입니다.")
        @DurationMin(
                seconds = 1,
                message = "auth.login-protection.lock-duration은 1초 이상이어야 합니다."
        )
        Duration lockDuration
) {
}
