package site.omagotchi.identityservice.global.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// 잘못된 JWT 설정은 바인딩 단계에서 검증해 애플리케이션 시작 차단
@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        @NotBlank(message = "auth.jwt.issuer는 비어 있을 수 없습니다.")
        String issuer,

        @NotBlank(message = "auth.jwt.audience는 비어 있을 수 없습니다.")
        String audience,

        @NotNull(message = "auth.jwt.access-token-ttl은 필수입니다.")
        @DurationMin(nanos = 1, message = "auth.jwt.access-token-ttl은 0보다 커야 합니다.")
        Duration accessTokenTtl,

        @NotNull(message = "auth.jwt.public-key-location은 필수입니다.")
        Resource publicKeyLocation,

        @NotNull(message = "auth.jwt.private-key-location은 필수입니다.")
        Resource privateKeyLocation
) {
}
