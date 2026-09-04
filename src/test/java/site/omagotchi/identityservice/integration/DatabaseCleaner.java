package site.omagotchi.identityservice.integration;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DatabaseCleaner {

    private static final List<String> DEFAULT_TABLES = List.of(
            "identity_service.email_verification_challenges",
            "identity_service.email_verification_scopes",
            "identity_service.email_delivery_cooldowns",
            "identity_service.account_role_change_audits",
            "identity_service.account_status_change_audits",
            "identity_service.refresh_tokens",
            "identity_service.accounts"
    );

    private final JdbcTemplate jdbcTemplate;
    private final List<String> tableNames = new ArrayList<>();

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        findTableNames();
    }

    private synchronized void findTableNames() {
        if (!tableNames.isEmpty()) {
            return;
        }
        try {
            List<String> discovered = jdbcTemplate.queryForList("""
                    SELECT quote_ident(table_schema) || '.' || quote_ident(table_name)
                    FROM information_schema.tables
                    WHERE table_schema = 'identity_service'
                      AND table_type = 'BASE TABLE'
                      AND table_name NOT IN ('flyway_schema_history', 'system_administrator_guards')
                    """, String.class);
            if (discovered != null && !discovered.isEmpty()) {
                tableNames.addAll(discovered);
            } else {
                tableNames.addAll(DEFAULT_TABLES);
            }
        } catch (Exception exception) {
            tableNames.addAll(DEFAULT_TABLES);
        }
    }

    public void clean() {
        if (tableNames.isEmpty()) {
            findTableNames();
        }
        if (!tableNames.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tableNames) + " CASCADE");
        }
    }

    public void cleanDatabase() {
        clean();
    }
}
