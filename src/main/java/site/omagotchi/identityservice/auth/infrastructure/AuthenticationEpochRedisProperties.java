package site.omagotchi.identityservice.auth.infrastructure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("auth.authentication-epoch.redis")
public record AuthenticationEpochRedisProperties(
        @NotBlank(message = "auth.authentication-epoch.redis.host는 비어 있을 수 없습니다.")
        String host,

        @Min(value = 1, message = "auth.authentication-epoch.redis.port는 1 이상이어야 합니다.")
        @Max(
                value = 65_535,
                message = "auth.authentication-epoch.redis.port는 65535 이하여야 합니다."
        )
        int port,

        @Min(value = 0, message = "auth.authentication-epoch.redis.database는 0 이상이어야 합니다.")
        int database,

        @NotBlank(message = "auth.authentication-epoch.redis.username은 비어 있을 수 없습니다.")
        String username,

        @NotBlank(message = "auth.authentication-epoch.redis.password는 비어 있을 수 없습니다.")
        String password,

        @NotNull(message = "auth.authentication-epoch.redis.connect-timeout은 필수입니다.")
        @DurationMin(
                nanos = 1,
                message = "auth.authentication-epoch.redis.connect-timeout은 0보다 커야 합니다."
        )
        Duration connectTimeout,

        @NotNull(message = "auth.authentication-epoch.redis.command-timeout은 필수입니다.")
        @DurationMin(
                nanos = 1,
                message = "auth.authentication-epoch.redis.command-timeout은 0보다 커야 합니다."
        )
        Duration commandTimeout,

        boolean sslEnabled
) {

    @Override
    public String toString() {
        return "AuthenticationEpochRedisProperties[host=" + host
                + ", port=" + port
                + ", database=" + database
                + ", username=" + username
                + ", password=[REDACTED]"
                + ", connectTimeout=" + connectTimeout
                + ", commandTimeout=" + commandTimeout
                + ", sslEnabled=" + sslEnabled + "]";
    }
}
