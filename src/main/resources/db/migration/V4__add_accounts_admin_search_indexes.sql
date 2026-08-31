-- 관리자 사용자 목록의 정렬·필터 경로 인덱스
--
-- 정렬은 항상 id를 마지막 Tie-breaker로 사용하므로 인덱스도 같은 컬럼 순서·방향으로 구성한다.
-- 부분 일치 검색(LIKE '%값%')은 B-Tree로 가속되지 않는다.
-- pg_trgm GIN 인덱스는 확장 설치 권한이 필요해 별도 Migration으로 분리한다.

CREATE INDEX idx_accounts_created_at_desc_id
    ON identity_service.accounts (created_at DESC, id ASC);

CREATE INDEX idx_accounts_name_id
    ON identity_service.accounts (name ASC, id ASC);

CREATE INDEX idx_accounts_status
    ON identity_service.accounts (status);

CREATE INDEX idx_accounts_global_role
    ON identity_service.accounts (global_role);
