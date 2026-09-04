ALTER TABLE identity_service.email_verification_scopes
    DROP CONSTRAINT ck_email_verification_scopes_purpose,
    ADD CONSTRAINT ck_email_verification_scopes_purpose
        CHECK (purpose IN (
            'SIGNUP',
            'PASSWORD_CHANGE',
            'PASSWORD_RESET',
            'ACCOUNT_RECOVERY'
        ));

ALTER TABLE identity_service.email_verification_challenges
    DROP CONSTRAINT ck_email_verification_challenges_purpose,
    ADD CONSTRAINT ck_email_verification_challenges_purpose
        CHECK (purpose IN (
            'SIGNUP',
            'PASSWORD_CHANGE',
            'PASSWORD_RESET',
            'ACCOUNT_RECOVERY'
        ));
