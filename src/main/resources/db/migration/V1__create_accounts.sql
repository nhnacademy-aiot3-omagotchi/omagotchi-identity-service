CREATE TABLE identity_service.accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(30) NOT NULL,
    global_role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts SMALLINT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_accounts_email UNIQUE (email),
    CONSTRAINT ck_accounts_email_normalized
        CHECK (email = LOWER(BTRIM(email))),
    CONSTRAINT ck_accounts_global_role
        CHECK (global_role IN ('USER', 'SYSTEM_ADMIN')),
    CONSTRAINT ck_accounts_status
        CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED', 'WITHDRAWN')),
    CONSTRAINT ck_accounts_failed_login_attempts
        CHECK (failed_login_attempts >= 0),
    CONSTRAINT ck_accounts_lock
        CHECK (
            (status = 'LOCKED' AND locked_until IS NOT NULL)
            OR (status <> 'LOCKED' AND locked_until IS NULL)
        ),
    CONSTRAINT ck_accounts_withdrawal
        CHECK (
            (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL)
            OR (status <> 'WITHDRAWN' AND withdrawn_at IS NULL)
        )
);
