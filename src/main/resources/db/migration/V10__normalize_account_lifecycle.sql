ALTER TABLE identity_service.accounts
    ADD COLUMN status_changed_at TIMESTAMPTZ;

UPDATE identity_service.accounts account
SET status_changed_at = CASE
    WHEN account.status = 'WITHDRAWN' THEN account.withdrawn_at
    WHEN account.status = 'DISABLED' THEN COALESCE(
        (
            SELECT MAX(audit.occurred_at)
            FROM identity_service.account_status_change_audits audit
            WHERE audit.target_user_id = account.id
              AND audit.action = 'ACCOUNT_DISABLED'
        ),
        account.created_at
    )
    ELSE COALESCE(
        (
            SELECT MAX(audit.occurred_at)
            FROM identity_service.account_status_change_audits audit
            WHERE audit.target_user_id = account.id
              AND audit.action = 'ACCOUNT_REACTIVATED'
        ),
        account.created_at
    )
END;

ALTER TABLE identity_service.accounts
    DROP CONSTRAINT ck_accounts_lock,
    DROP CONSTRAINT ck_accounts_withdrawal,
    DROP CONSTRAINT ck_accounts_status;

UPDATE identity_service.accounts
SET status = 'ACTIVE'
WHERE status = 'LOCKED';

ALTER TABLE identity_service.accounts
    ALTER COLUMN status_changed_at SET NOT NULL,
    DROP COLUMN withdrawn_at,
    ADD CONSTRAINT ck_accounts_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'WITHDRAWN')),
    ADD CONSTRAINT ck_accounts_lock
        CHECK (
            (
                status = 'ACTIVE'
                AND (locked_until IS NULL OR failed_login_attempts > 0)
            )
            OR (
                status <> 'ACTIVE'
                AND failed_login_attempts = 0
                AND locked_until IS NULL
            )
        );

DROP INDEX identity_service.idx_accounts_usable_system_admin;

CREATE INDEX idx_accounts_active_system_admin
    ON identity_service.accounts (id)
    WHERE global_role = 'SYSTEM_ADMIN'
      AND status = 'ACTIVE';

CREATE INDEX idx_accounts_withdrawn_status_changed
    ON identity_service.accounts (status_changed_at, id)
    WHERE status = 'WITHDRAWN';
