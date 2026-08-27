package site.omagotchi.identityservice;

import com.resend.Resend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.identityservice.integration.TestJwtConfig;
import site.omagotchi.identityservice.integration.TestcontainersConfig;

import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

@DisplayName("Identity Service 설정")
@Import({TestcontainersConfig.class, TestJwtConfig.class})
@ActiveProfiles("test")
@SpringBootTest
class IdentityServiceApplicationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private Resend resendClient;

    @Test
    @DisplayName("Redis & Resend 이메일 인프라 빈 등록")
    void registersEmailInfrastructureBeans() {
        then(stringRedisTemplate).isNotNull();
        then(resendClient).isNotNull();
    }

    @Test
    @DisplayName("PostgreSQL 18.1 Flyway V1·V2")
    void appliesMigrationsOnProjectPostgreSqlVersion() {
        // Given
        String expectedVersionPrefix = "18.1";
        String expectedAccountsTable = "identity_service.accounts";
        String expectedRefreshTokensTable = "identity_service.refresh_tokens";

        // When
        String serverVersion = jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version')",
                String.class
        );
        String accountsTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('identity_service.accounts')::text",
                String.class
        );
        String refreshTokensTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('identity_service.refresh_tokens')::text",
                String.class
        );
        String accountIdType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'identity_service'
                  AND table_name = 'accounts'
                  AND column_name = 'id'
                """, String.class);
        String refreshTokenAccountIdType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'identity_service'
                  AND table_name = 'refresh_tokens'
                  AND column_name = 'account_id'
                """, String.class);
        List<String> migrationVersions = jdbcTemplate.queryForList("""
                SELECT version
                FROM identity_service.flyway_schema_history
                WHERE success = TRUE
                  AND version IS NOT NULL
                ORDER BY installed_rank
                """, String.class);

        // Then
        thenSoftly(softly -> {
            softly.then(serverVersion).startsWith(expectedVersionPrefix);
            softly.then(accountsTable).isEqualTo(expectedAccountsTable);
            softly.then(refreshTokensTable).isEqualTo(expectedRefreshTokensTable);
            softly.then(accountIdType).isEqualTo("uuid");
            softly.then(refreshTokenAccountIdType).isEqualTo("uuid");
            softly.then(migrationVersions).containsExactly("1", "2");
        });
    }
}
