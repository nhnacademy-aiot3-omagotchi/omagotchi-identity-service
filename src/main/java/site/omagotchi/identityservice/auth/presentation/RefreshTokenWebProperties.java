package site.omagotchi.identityservice.auth.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "auth.refresh-token")
public record RefreshTokenWebProperties(
        boolean secure,

        @NotEmpty(message = "auth.refresh-token.allowed-origins는 하나 이상이어야 합니다.")
        Set<@NotBlank String> allowedOrigins
) {
}
