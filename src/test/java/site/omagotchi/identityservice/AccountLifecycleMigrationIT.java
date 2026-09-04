package site.omagotchi.identityservice;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;

class AccountLifecycleMigrationIT {

    private static final String ACTOR_ID = "00000000-0000-0000-0000-000000000001";
    private static final String TARGET_ID = "00000000-0000-0000-0000-000000000002";
    private static final String WITHDRAWN_ID = "00000000-0000-0000-0000-000000000003";

    @Test
    @DisplayName("V7 기존 계정과 감사 데이터를 V10 최종 모델로 전환")
    void migratesExistingLifecycleData() throws Exception {
        PostgreSQLContainer container = new PostgreSQLContainer(
                DockerImageName.parse("postgres:18.1")
        );
        try {
            container.start();
            migrate(container, "7");
            insertLegacyData(container);

            migrate(container, null);

            assertMigratedData(container);
        } finally {
            container.stop();
        }
    }

    private void migrate(PostgreSQLContainer container, String target) {
        var configuration = Flyway.configure()
                .dataSource(
                        container.getJdbcUrl(),
                        container.getUsername(),
                        container.getPassword()
                )
                .locations("classpath:db/migration")
                .defaultSchema("identity_service")
                .schemas("identity_service")
                .createSchemas(true)
                .validateOnMigrate(true)
                .configuration(Map.of(
                        "flyway.postgresql.transactional.lock", "false"
                ));
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private void insertLegacyData(PostgreSQLContainer container) throws Exception {
        try (Connection connection = connection(container);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO identity_service.accounts (
                        id, email, password_hash, name, global_role, status,
                        failed_login_attempts, locked_until, withdrawn_at,
                        created_at, updated_at
                    ) VALUES
                    ('%s', 'admin@example.com', 'hash', '관리자', 'SYSTEM_ADMIN',
                        'ACTIVE', 0, NULL, NULL,
                        '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z'),
                    ('%s', 'locked@example.com', 'hash', '잠긴 사용자', 'USER',
                        'LOCKED', 5, '2026-09-05T00:00:00Z', NULL,
                        '2026-08-02T00:00:00Z', '2026-08-03T00:00:00Z'),
                    ('%s', 'withdrawn@example.com', 'hash', '탈퇴 사용자', 'USER',
                        'WITHDRAWN', 0, NULL, '2026-08-20T00:00:00Z',
                        '2026-08-04T00:00:00Z', '2026-08-20T00:00:00Z')
                    """.formatted(ACTOR_ID, TARGET_ID, WITHDRAWN_ID));
            statement.execute("""
                    INSERT INTO identity_service.account_status_change_audits (
                        actor_user_id, target_user_id, action,
                        before_status, after_status, reason, occurred_at
                    ) VALUES (
                        '%s', '%s', 'ACCOUNT_UNLOCKED',
                        'LOCKED', 'ACTIVE', '본인 확인', '2026-08-10T00:00:00Z'
                    )
                    """.formatted(ACTOR_ID, TARGET_ID));
            statement.execute("""
                    INSERT INTO identity_service.account_role_change_audits (
                        actor_user_id, target_user_id, action,
                        before_role, after_role, reason, occurred_at
                    ) VALUES (
                        '%s', '%s', 'ROLE_GRANTED',
                        'USER', 'SYSTEM_ADMIN', '운영 인수인계', '2026-08-11T00:00:00Z'
                    )
                    """.formatted(ACTOR_ID, TARGET_ID));
        }
    }

    private void assertMigratedData(PostgreSQLContainer container) throws Exception {
        try (Connection connection = connection(container);
             Statement statement = connection.createStatement()) {
            ResultSet lockedAccount = statement.executeQuery("""
                    SELECT status, failed_login_attempts, locked_until, status_changed_at
                    FROM identity_service.accounts
                    WHERE id = '%s'
                    """.formatted(TARGET_ID));
            lockedAccount.next();
            String status = lockedAccount.getString("status");
            short failedLoginAttempts = lockedAccount.getShort("failed_login_attempts");
            Instant lockedUntil = lockedAccount
                    .getObject("locked_until", OffsetDateTime.class)
                    .toInstant();
            Instant statusChangedAt = lockedAccount
                    .getObject("status_changed_at", OffsetDateTime.class)
                    .toInstant();

            ResultSet withdrawnAccount = statement.executeQuery("""
                    SELECT status_changed_at
                    FROM identity_service.accounts
                    WHERE id = '%s'
                    """.formatted(WITHDRAWN_ID));
            withdrawnAccount.next();
            Instant withdrawnStatusChangedAt = withdrawnAccount
                    .getObject("status_changed_at", OffsetDateTime.class)
                    .toInstant();

            ResultSet removedColumns = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'identity_service'
                      AND (
                          (table_name = 'account_status_change_audits'
                              AND column_name IN ('before_status', 'after_status'))
                          OR
                          (table_name = 'account_role_change_audits'
                              AND column_name IN ('before_role', 'after_role'))
                      )
                    """);
            removedColumns.next();
            int redundantColumnCount = removedColumns.getInt(1);

            ResultSet statusAudit = statement.executeQuery("""
                    SELECT action, before_value, after_value
                    FROM identity_service.account_permission_change_audits
                    WHERE audit_type = 'ACCOUNT_STATUS'
                    """);
            statusAudit.next();
            String statusAction = statusAudit.getString("action");
            String statusBefore = statusAudit.getString("before_value");
            String statusAfter = statusAudit.getString("after_value");

            ResultSet roleAudit = statement.executeQuery("""
                    SELECT before_value, after_value
                    FROM identity_service.account_permission_change_audits
                    WHERE audit_type = 'ACCOUNT_ROLE'
                    """);
            roleAudit.next();
            String roleBefore = roleAudit.getString("before_value");
            String roleAfter = roleAudit.getString("after_value");

            thenSoftly(softly -> {
                softly.then(status).isEqualTo("ACTIVE");
                softly.then(failedLoginAttempts).isEqualTo((short) 5);
                softly.then(lockedUntil)
                        .isEqualTo(Instant.parse("2026-09-05T00:00:00Z"));
                softly.then(statusChangedAt)
                        .isEqualTo(Instant.parse("2026-08-02T00:00:00Z"));
                softly.then(withdrawnStatusChangedAt)
                        .isEqualTo(Instant.parse("2026-08-20T00:00:00Z"));
                softly.then(redundantColumnCount).isZero();
                softly.then(statusAction).isEqualTo("LOGIN_LOCK_RELEASED");
                softly.then(statusBefore).isEqualTo("ACTIVE");
                softly.then(statusAfter).isEqualTo("ACTIVE");
                softly.then(roleBefore).isEqualTo("USER");
                softly.then(roleAfter).isEqualTo("SYSTEM_ADMIN");
            });
        }
    }

    private Connection connection(PostgreSQLContainer container) throws Exception {
        return DriverManager.getConnection(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword()
        );
    }
}
