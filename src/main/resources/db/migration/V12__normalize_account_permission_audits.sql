DROP VIEW identity_service.account_permission_change_audits;

ALTER TABLE identity_service.account_status_change_audits
    DROP CONSTRAINT ck_account_status_change_audits_action,
    DROP CONSTRAINT ck_account_status_change_audits_transition,
    DROP CONSTRAINT ck_account_status_change_audits_before_status,
    DROP CONSTRAINT ck_account_status_change_audits_after_status;

UPDATE identity_service.account_status_change_audits
SET action = 'LOGIN_LOCK_RELEASED'
WHERE action = 'ACCOUNT_UNLOCKED';

ALTER TABLE identity_service.account_status_change_audits
    DROP COLUMN before_status,
    DROP COLUMN after_status,
    ADD CONSTRAINT ck_account_status_change_audits_action
        CHECK (action IN (
            'ACCOUNT_DISABLED',
            'LOGIN_LOCK_RELEASED',
            'ACCOUNT_REACTIVATED',
            'ACCOUNT_WITHDRAWN',
            'ACCOUNT_RECOVERED'
        ));

ALTER TABLE identity_service.account_role_change_audits
    DROP CONSTRAINT ck_account_role_change_audits_transition,
    DROP CONSTRAINT ck_account_role_change_audits_before_role,
    DROP CONSTRAINT ck_account_role_change_audits_after_role,
    DROP COLUMN before_role,
    DROP COLUMN after_role;

-- 전이 전후 값의 원본인 action을 사용한 기존 통합 조회 계약 유지
CREATE VIEW identity_service.account_permission_change_audits AS
SELECT
    'ACCOUNT_STATUS' AS audit_type,
    id               AS source_id,
    actor_user_id,
    target_user_id,
    action,
    CASE action
        WHEN 'ACCOUNT_DISABLED' THEN 'ACTIVE'
        WHEN 'LOGIN_LOCK_RELEASED' THEN 'ACTIVE'
        WHEN 'ACCOUNT_REACTIVATED' THEN 'DISABLED'
        WHEN 'ACCOUNT_WITHDRAWN' THEN 'ACTIVE'
        WHEN 'ACCOUNT_RECOVERED' THEN 'WITHDRAWN'
    END::VARCHAR(20) AS before_value,
    CASE action
        WHEN 'ACCOUNT_DISABLED' THEN 'DISABLED'
        WHEN 'LOGIN_LOCK_RELEASED' THEN 'ACTIVE'
        WHEN 'ACCOUNT_REACTIVATED' THEN 'ACTIVE'
        WHEN 'ACCOUNT_WITHDRAWN' THEN 'WITHDRAWN'
        WHEN 'ACCOUNT_RECOVERED' THEN 'ACTIVE'
    END::VARCHAR(20) AS after_value,
    reason,
    occurred_at,
    request_id
FROM identity_service.account_status_change_audits
UNION ALL
SELECT
    'ACCOUNT_ROLE',
    id,
    actor_user_id,
    target_user_id,
    action,
    CASE action
        WHEN 'ROLE_GRANTED' THEN 'USER'
        WHEN 'ROLE_REVOKED' THEN 'SYSTEM_ADMIN'
    END::VARCHAR(20),
    CASE action
        WHEN 'ROLE_GRANTED' THEN 'SYSTEM_ADMIN'
        WHEN 'ROLE_REVOKED' THEN 'USER'
    END::VARCHAR(20),
    reason,
    occurred_at,
    request_id
FROM identity_service.account_role_change_audits;
