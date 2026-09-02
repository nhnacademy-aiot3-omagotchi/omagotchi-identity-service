-- 계정 상태·전역 역할 감사의 통합 조회 뷰
--
-- 두 감사는 전이 규칙이 겹치지 않아 테이블을 나눴다(V3, V6). 대신 "누가 언제 무엇을
-- 바꿨나"는 한 화면에서 시간순으로 봐야 하므로 조회만 여기서 합친다.
-- 뷰는 읽기 전용이다. 쓰기는 각 테이블의 CHECK 를 그대로 통과해야 한다.
--
-- source_id 는 원본 테이블별 PK 라 뷰 전체에서 유일하지 않다.
-- 그래서 (audit_type, source_id)를 뷰의 식별자로 쓴다.
CREATE VIEW identity_service.account_permission_change_audits AS
SELECT
    'ACCOUNT_STATUS' AS audit_type,
    id               AS source_id,
    actor_user_id,
    target_user_id,
    action,
    before_status    AS before_value,
    after_status     AS after_value,
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
    before_role,
    after_role,
    reason,
    occurred_at,
    request_id
FROM identity_service.account_role_change_audits;

-- 통합 뷰는 두 테이블을 occurred_at 으로 정렬한다. 상태 감사에는 그 인덱스가 없어
-- 전량 정렬로 떨어지므로 역할 감사(V6)와 같은 인덱스를 맞춰 준다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_status_change_audits_occurred_at
    ON identity_service.account_status_change_audits (occurred_at DESC, id DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_role_change_audits_occurred_at
    ON identity_service.account_role_change_audits (occurred_at DESC, id DESC);
