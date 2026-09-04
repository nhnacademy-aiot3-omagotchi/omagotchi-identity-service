-- Identity OTP 트래픽을 중지한 maintenance 단계에서 실행한다.
-- 잠금을 즉시 얻지 못하면 전체 Migration을 Rollback하고 blocker 확인 후 재시도한다.
SET LOCAL lock_timeout = '5s';

LOCK TABLE identity_service.email_verification_scopes
    IN ACCESS EXCLUSIVE MODE;

-- V8 백필 뒤 구버전 인스턴스가 변경한 쿨다운을 잠금 획득 후 최종 반영한다.
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
ORDER BY email, next_issue_at DESC, updated_at DESC, id
ON CONFLICT (email) DO UPDATE
SET active_challenge_id = EXCLUDED.active_challenge_id,
    next_issue_at = EXCLUDED.next_issue_at,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

ALTER TABLE identity_service.email_verification_scopes
    DROP CONSTRAINT ck_email_verification_scopes_purpose;

ALTER TABLE identity_service.email_verification_scopes
    ADD CONSTRAINT ck_email_verification_scopes_purpose
        CHECK (purpose IN ('SIGNUP', 'PASSWORD_CHANGE', 'PASSWORD_RESET'));

ALTER TABLE identity_service.email_verification_challenges
    DROP CONSTRAINT ck_email_verification_challenges_purpose;

ALTER TABLE identity_service.email_verification_challenges
    ADD CONSTRAINT ck_email_verification_challenges_purpose
        CHECK (purpose IN ('SIGNUP', 'PASSWORD_CHANGE', 'PASSWORD_RESET'));

ALTER TABLE identity_service.email_verification_scopes
    DROP CONSTRAINT ck_email_verification_scopes_next_issue,
    DROP COLUMN next_issue_at;
