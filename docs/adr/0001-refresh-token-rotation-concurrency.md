# ADR 0001: Refresh Token 회전 동시성 정책

- 상태: Proposed
- 작성일: 2026-07-27

## 배경

Identity Service는 로그인할 때 불투명한 Refresh Token을 발급하고 DB에는 해시만 저장합니다. 갱신할 때 기존 Token을 사용 처리하고 같은 `familyId`로 다음 Token을 발급합니다. 이미 사용한 Token이 다시 들어오면 탈취 후 재사용일 수 있으므로 같은 family를 모두 폐기합니다.

브라우저의 중복 요청, 네트워크 재시도와 복수 애플리케이션 인스턴스에서도 두 요청이 같은 Refresh Token을 거의 동시에 사용할 수 있습니다. 이때 정상적인 중복 요청과 탈취자의 재사용을 서버가 Token 값만으로 구분할 수는 없습니다.

## 현재 구현

```text
Login
  → 새 familyId와 7일 고정 만료 시각 생성
  → Refresh Token 원문은 Cookie로 전달
  → DB에는 SHA-256 해시를 가진 Token 행 저장

Refresh
  → token_hash로 현재 행 SELECT FOR UPDATE
  → 미사용 Token이면 used_at 기록
  → 같은 familyId와 만료 시각으로 다음 Token 행 생성
  → 사용된 Token이면 family 전체 폐기
```

- Refresh Token을 발급할 때마다 새 행을 저장합니다.
- `familyId`는 한 번의 로그인을 시작으로 이어진 Token 계열을 식별합니다.
- family 만료 시각은 최초 로그인으로부터 기본 7일이며, 회전해도 연장하지 않습니다.
- 현재 Token 행을 비관적 쓰기 잠금으로 조회하므로 같은 DB를 사용하는 여러 Identity 인스턴스에서도 같은 Token의 사용 처리가 직렬화됩니다.
- `replacedByTokenId`는 저장하지 않습니다. 현재 보안 판단은 다음 Token의 정확한 행보다 family 전체를 대상으로 하기 때문입니다.
- 로그인 세션을 대표하는 별도 고정 행은 없습니다.

## 동시 갱신의 현재 결과

같은 Refresh Token으로 두 요청 A와 B가 동시에 들어오면 다음 순서가 가능합니다.

1. A가 Token 행을 잠그고 사용 처리한 뒤 다음 Token을 발급합니다.
2. B는 잠금이 풀릴 때까지 기다립니다.
3. B는 이미 사용된 Token을 확인하고 재사용으로 판단하여 family 전체를 폐기합니다.
4. A가 받은 새 Refresh Token도 폐기되어 다음 갱신에는 사용할 수 없습니다.
5. A가 함께 받은 Access JWT는 별도 denylist가 없으므로 만료 시각까지 유효합니다.

이 정책은 탈취 후 재사용을 엄격하게 막지만, 사용자의 실수나 네트워크 재시도만으로도 해당 로그인 세션이 종료될 수 있습니다. 현재 테스트는 순차 재사용을 검증하며, 실제 두 트랜잭션의 동시 실행은 아직 검증하지 않습니다.

## 고려할 대안

### 대안 1: 현재 구조와 엄격한 재사용 폐기 유지

**장점**

- 이미 구현한 Token 행 잠금과 family 폐기를 그대로 사용합니다.
- 복수 인스턴스에서도 DB가 동시성 직렬화 지점이 됩니다.
- 탈취자의 재사용을 정상 재시도로 잘못 허용하지 않습니다.

**비용**

- 정상적인 중복 갱신에도 다시 로그인해야 할 수 있습니다.
- 회전할 때마다 Token 행이 쌓이므로 보존·삭제 정책이 필요합니다.

### 대안 2: 별도 `refresh_sessions` 테이블과 로그인당 고정 행 추가

기존 `refresh_tokens`에 Token 행 하나를 더 추가하는 방식이 아닙니다. 별도 `refresh_sessions` 테이블을 만들고, 로그인할 때마다 해당 로그인을 대표하는 고정 행 하나를 생성합니다.

```text
refresh_sessions
  id
  account_id
  current_token_id 또는 current_generation
  expires_at
  revoked_at
  revocation_reason

refresh_tokens
  id
  session_id
  token_hash
  generation
  used_at
  created_at
```

모든 갱신 요청은 Token이 회전해도 같은 `refresh_sessions` 행을 먼저 잠급니다. 세션 행에서 현재 Token의 세대, 만료와 폐기 상태를 관리하고, `refresh_tokens`에는 현재 Token과 필요한 범위의 회전 이력을 저장합니다.

**장점**

- 잠금과 세션 상태의 기준 행이 Token 회전 후에도 바뀌지 않습니다.
- 두 트랜잭션이 동시에 같은 로그인 세션을 다음 세대로 진행하는 것을 막습니다.
- 계정별 세션 목록, 기기별 로그아웃과 세션 만료 관리로 확장하기 쉽습니다.
- 현재 Token과 과거 Token 이력의 책임을 분리할 수 있습니다.

**비용**

- 테이블, Entity와 조회 경로가 늘어납니다.
- 고정 세션 행은 동시 상태 변경을 직렬화하지만, 정상 중복 요청과 탈취 재사용을 구분하지는 못합니다.
- 중복 요청에 같은 결과를 반환하려면 새 Token 원문을 다시 제공할 별도 설계가 필요하며, 해시만 저장하는 현재 원칙과 충돌할 수 있습니다.

### 대안 3: Redis 또는 분산 잠금 사용

**장점**

- 짧은 중복 요청 창과 임시 결과를 빠르게 관리할 수 있습니다.

**비용**

- 현재 PostgreSQL 행 잠금으로 해결되는 범위에 새 운영 의존성을 추가합니다.
- DB 상태와 잠금 저장소 사이의 실패·복구 정책이 필요합니다.

## 결정할 내용

4주차 구현에서 다음을 확인한 뒤 이 ADR을 `Accepted`로 변경하거나 새 결정으로 대체합니다.

- 실제 동시 Refresh 요청을 두 트랜잭션으로 실행하는 통합 테스트
- 엄격한 family 폐기를 MVP 사용자 경험으로 수용할지 여부
- 계정별 세션 조회·개별 로그아웃이 가까운 일정에 필요한지 여부
- 만료·폐기된 Refresh Token 행의 보존 및 정리 기준
- 고정 로그인 세션 행이 현재 요구보다 복잡도를 더 많이 늘리는지 여부

MVP에서는 Redis를 추가하지 않습니다. 고정 로그인 세션 행도 동시 재시도 문제를 자동으로 해결하는 수단으로 보지 않고, 실제 세션 관리 요구가 확인될 때 도입을 판단합니다.

## 완료 조건

- 동시 갱신 테스트로 현재 DB 잠금과 family 폐기 결과를 재현합니다.
- 선택한 정책과 사용자에게 보이는 결과를 이 문서에 확정합니다.
- 스키마가 바뀌면 새 Flyway Migration과 회귀 테스트를 함께 추가합니다.
- 기존 V1·V2 Migration은 이미 적용 가능한 이력으로 보고 수정하지 않습니다.

## 관련 문서

- 중앙 ADR `0003`: 브라우저 직접 JWT 인증 경계
- 중앙 ADR `0008`: 계정 UUID와 JWT Subject 식별자
- 중앙 인증·인가 명세: `10-specifications/01-identity/02-authentication-authorization.md`
- 구현: `RefreshTokenRotation`, `RefreshTokenStore`, `RefreshTokenJpaRepository`
