package site.omagotchi.identityservice.auth.application.session;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.refresh-token")
public record RefreshTokenProperties(

        @NotNull(message = "auth.refresh-token.ttl은 필수입니다.")
        @DurationMin(seconds = 1, message = "auth.refresh-token.ttl은 1초 이상이어야 합니다.")
        Duration ttl
) {
}
