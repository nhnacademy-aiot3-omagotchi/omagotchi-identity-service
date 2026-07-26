package site.omagotchi.identityservice.auth.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "auth.refresh-token")
public record RefreshTokenProperties(

        @NotNull(message = "auth.refresh-token.ttl은 필수입니다.")
        @DurationMin(seconds = 1, message = "auth.refresh-token.ttl은 1초 이상이어야 합니다.")
        Duration ttl,

        boolean secure,

        @NotEmpty(message = "auth.refresh-token.allowed-origins는 하나 이상이어야 합니다.")
        Set<@NotBlank String> allowedOrigins
) {
}
