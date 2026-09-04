CREATE TABLE identity_service.email_delivery_cooldowns (
    email VARCHAR(254) PRIMARY KEY,
    active_challenge_id UUID,
    next_issue_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_email_delivery_cooldowns_email_normalized
        CHECK (email = LOWER(BTRIM(email))),
    CONSTRAINT ck_email_delivery_cooldowns_next_issue
        CHECK (next_issue_at >= created_at)
);

INSERT INTO identity_service.email_delivery_cooldowns (
    email,
    active_challenge_id,
    next_issue_at,
    created_at,
    updated_at
)
SELECT DISTINCT ON (email)
    email,
    active_challenge_id,
    next_issue_at,
    created_at,
    updated_at
FROM identity_service.email_verification_scopes
ORDER BY email, next_issue_at DESC, updated_at DESC, id;
