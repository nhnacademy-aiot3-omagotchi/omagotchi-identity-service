package site.omagotchi.identityservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.identityservice.integration.TestJwtConfig;
import site.omagotchi.identityservice.integration.TestcontainersConfig;

import java.util.List;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

@DisplayName("Identity Service 설정")
@Import({TestcontainersConfig.class, TestJwtConfig.class})
@ActiveProfiles("test")
@SpringBootTest
class IdentityServiceApplicationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("PostgreSQL 18.1 Flyway V1~V12")
    void appliesMigrationsOnProjectPostgreSqlVersion() {
        // Given
        String expectedVersionPrefix = "18.1";
        String expectedAccountsTable = "identity_service.accounts";
        String expectedRefreshTokensTable = "identity_service.refresh_tokens";
        String expectedAccountStatusAuditsTable =
                "identity_service.account_status_change_audits";
        String expectedSystemAdministratorGuardsTable =
                "identity_service.system_administrator_guards";
        String expectedEmailDeliveryCooldownsTable =
                "identity_service.email_delivery_cooldowns";

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
        String accountStatusAuditsTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('identity_service.account_status_change_audits')::text",
                String.class
        );
        String systemAdministratorGuardsTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('identity_service.system_administrator_guards')::text",
                String.class
        );
        String emailDeliveryCooldownsTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('identity_service.email_delivery_cooldowns')::text",
                String.class
        );
        Integer systemAdministratorGuardCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_service.system_administrator_guards",
                Integer.class
        );
        Integer systemAdministratorGuardId = jdbcTemplate.queryForObject(
                "SELECT id FROM identity_service.system_administrator_guards",
                Integer.class
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
        String auditRequestIdType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'identity_service'
                  AND table_name = 'account_status_change_audits'
                  AND column_name = 'request_id'
                """, String.class);
        Integer auditRequestIdLength = jdbcTemplate.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'identity_service'
                  AND table_name = 'account_status_change_audits'
                  AND column_name = 'request_id'
                """, Integer.class);
        String auditRequestIdNullable = jdbcTemplate.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'identity_service'
                  AND table_name = 'account_status_change_audits'
                  AND column_name = 'request_id'
                """, String.class);
        List<String> auditConstraints = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'identity_service'
                  AND table_name = 'account_status_change_audits'
                ORDER BY constraint_name
                """, String.class);
        List<String> redundantAuditColumns = jdbcTemplate.queryForList("""
                SELECT table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema = 'identity_service'
                  AND (
                      (table_name = 'account_status_change_audits'
                          AND column_name IN ('before_status', 'after_status'))
                      OR
                      (table_name = 'account_role_change_audits'
                          AND column_name IN ('before_role', 'after_role'))
                  )
                ORDER BY table_name, column_name
                """, String.class);
        List<String> systemAdministratorGuardConstraints = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'identity_service'
                  AND table_name = 'system_administrator_guards'
                ORDER BY constraint_name
                """, String.class);
        String activeAdministratorIndex = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'identity_service'
                  AND tablename = 'accounts'
                  AND indexname = 'idx_accounts_active_system_admin'
                """, String.class);
        String emailVerificationScopePurposeConstraint = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname = 'ck_email_verification_scopes_purpose'
                  AND conrelid = 'identity_service.email_verification_scopes'::regclass
                """, String.class);
        String emailVerificationChallengePurposeConstraint = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname = 'ck_email_verification_challenges_purpose'
                  AND conrelid = 'identity_service.email_verification_challenges'::regclass
                """, String.class);
        Integer legacyScopeCooldownColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'identity_service'
                  AND table_name = 'email_verification_scopes'
                  AND column_name = 'next_issue_at'
                """, Integer.class);
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
            softly.then(accountStatusAuditsTable)
                    .isEqualTo(expectedAccountStatusAuditsTable);
            softly.then(systemAdministratorGuardsTable)
                    .isEqualTo(expectedSystemAdministratorGuardsTable);
            softly.then(emailDeliveryCooldownsTable)
                    .isEqualTo(expectedEmailDeliveryCooldownsTable);
            softly.then(systemAdministratorGuardCount).isEqualTo(1);
            softly.then(systemAdministratorGuardId).isEqualTo(1);
            softly.then(accountIdType).isEqualTo("uuid");
            softly.then(refreshTokenAccountIdType).isEqualTo("uuid");
            softly.then(auditRequestIdType).isEqualTo("character varying");
            softly.then(auditRequestIdLength).isEqualTo(32);
            softly.then(auditRequestIdNullable).isEqualTo("YES");
            softly.then(auditConstraints).contains(
                    "fk_account_status_change_audits_actor",
                    "fk_account_status_change_audits_target",
                    "ck_account_status_change_audits_reason"
            );
            softly.then(redundantAuditColumns).isEmpty();
            softly.then(systemAdministratorGuardConstraints).contains(
                    "ck_system_administrator_guards_singleton",
                    "system_administrator_guards_pkey"
            );
            softly.then(activeAdministratorIndex)
                    .contains("global_role", "SYSTEM_ADMIN", "ACTIVE")
                    .doesNotContain("LOCKED");
            softly.then(emailVerificationScopePurposeConstraint)
                    .contains(
                            "SIGNUP",
                            "PASSWORD_CHANGE",
                            "PASSWORD_RESET",
                            "ACCOUNT_RECOVERY"
                    );
            softly.then(emailVerificationChallengePurposeConstraint)
                    .contains(
                            "SIGNUP",
                            "PASSWORD_CHANGE",
                            "PASSWORD_RESET",
                            "ACCOUNT_RECOVERY"
                    );
            softly.then(legacyScopeCooldownColumnCount).isZero();
            softly.then(migrationVersions)
                    .containsExactly(
                            "1", "2", "3", "4", "5", "6",
                            "7", "8", "9", "10", "11", "12"
                    );
        });
    }
}
