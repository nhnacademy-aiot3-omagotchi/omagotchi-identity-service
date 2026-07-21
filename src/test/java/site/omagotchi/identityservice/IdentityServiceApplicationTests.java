package site.omagotchi.identityservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

@DisplayName("Identity Service 설정")
@Import({TestcontainersConfiguration.class, TestJwtConfiguration.class})
@ActiveProfiles("test")
@SpringBootTest
class IdentityServiceApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("PostgreSQL 18.1 초기 Flyway Migration")
    void appliesInitialMigrationOnProjectPostgreSqlVersion() {
        // Given
        String expectedVersionPrefix = "18.1";
        String expectedAccountsTable = "identity_service.accounts";

        // When
        String serverVersion = jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version')",
                String.class
        );
        String accountsTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('identity_service.accounts')::text",
                String.class
        );

        // Then
        thenSoftly(softly -> {
            softly.then(serverVersion).startsWith(expectedVersionPrefix);
            softly.then(accountsTable).isEqualTo(expectedAccountsTable);
        });
    }
}
