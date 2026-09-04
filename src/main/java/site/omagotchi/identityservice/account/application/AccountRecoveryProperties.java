package site.omagotchi.identityservice.account.application;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;

@Validated
@ConfigurationProperties(prefix = "account.recovery")
public record AccountRecoveryProperties(
        @NotNull(message = "account.recovery.policy-effective-at은 필수입니다.")
        Instant policyEffectiveAt
) {
}
