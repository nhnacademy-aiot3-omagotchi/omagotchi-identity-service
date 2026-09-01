CREATE TABLE identity_service.email_verification_scopes (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    active_challenge_id UUID,
    next_issue_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_email_verification_scopes_email_purpose
        UNIQUE (email, purpose),
    CONSTRAINT ck_email_verification_scopes_purpose
        CHECK (purpose IN ('SIGNUP', 'PASSWORD_CHANGE')),
    CONSTRAINT ck_email_verification_scopes_next_issue
        CHECK (next_issue_at >= created_at)
);

CREATE TABLE identity_service.email_verification_challenges (
    id UUID PRIMARY KEY,
    scope_id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    code_mac VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    failed_attempts SMALLINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_email_verification_challenges_scope
        FOREIGN KEY (scope_id)
            REFERENCES identity_service.email_verification_scopes (id),
    CONSTRAINT ck_email_verification_challenges_purpose
        CHECK (purpose IN ('SIGNUP', 'PASSWORD_CHANGE')),
    CONSTRAINT ck_email_verification_challenges_status
        CHECK (status IN ('OPEN', 'CONSUMED', 'EXHAUSTED', 'SUPERSEDED')),
    CONSTRAINT ck_email_verification_challenges_delivery_status
        CHECK (delivery_status IN ('PENDING', 'ACCEPTED', 'FAILED')),
    CONSTRAINT ck_email_verification_challenges_failed_attempts
        CHECK (failed_attempts >= 0),
    CONSTRAINT ck_email_verification_challenges_expiration
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_email_verification_challenges_scope_created
    ON identity_service.email_verification_challenges (scope_id, created_at DESC);

CREATE INDEX idx_email_verification_challenges_expiration
    ON identity_service.email_verification_challenges (expires_at);
