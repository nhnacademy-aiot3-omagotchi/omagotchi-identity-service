# ADR 0001: Refresh Token 회전 동시성 정책

- 상태: Accepted
- 작성일: 2026-07-27
- 결정일: 2026-07-27

> 후속 결정: 사용자 전체 Refresh Session 폐기와 서로 다른 Token 세대의 경합은
> [ADR 0002](0002-account-authentication-refresh-session-serialization.md)의 계정 행 공통 잠금으로 보완합니다.
> 이 문서의 엄격한 재사용 감지와 Token family 폐기 정책은 유지합니다.

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
  → token_hash로 account_id 값만 조회
  → Account SELECT FOR UPDATE
  → 같은 token_hash를 RefreshToken SELECT FOR UPDATE로 다시 조회·검증
  → 미사용 Token이면 used_at 기록
  → 같은 familyId와 만료 시각으로 다음 Token 행 생성
  → 사용된 Token이면 family 전체 폐기
```

- Refresh Token을 발급할 때마다 새 행을 저장합니다.
- `familyId`는 한 번의 로그인을 시작으로 이어진 Token 계열을 식별합니다.
- family 만료 시각은 최초 로그인으로부터 기본 7일이며, 회전해도 연장하지 않습니다.
- 계정 행을 먼저 비관적 쓰기 잠금으로 조회한 뒤 현재 Token 행을 잠그므로 같은 DB를 사용하는 여러 Identity 인스턴스에서도 같은 계정의 인증과 Refresh Session 변경이 직렬화됩니다.
- `replacedByTokenId`는 저장하지 않습니다. 현재 보안 판단은 다음 Token의 정확한 행보다 family 전체를 대상으로 하기 때문입니다.
- 로그인 세션을 대표하는 별도 고정 행은 없습니다.

## 동시 갱신의 현재 결과

같은 Refresh Token으로 두 요청 A와 B가 동시에 들어오면 다음 순서가 가능합니다.

1. A가 Account 행과 Token 행을 순서대로 잠그고 사용 처리한 뒤 다음 Token을 발급합니다.
2. B는 같은 Account 행의 잠금이 풀릴 때까지 기다립니다.
3. B는 이미 사용된 Token을 확인하고 재사용으로 판단하여 family 전체를 폐기합니다.
4. A가 받은 새 Refresh Token도 폐기되어 다음 갱신에는 사용할 수 없습니다.
5. A가 함께 받은 Access JWT는 별도 denylist가 없으므로 만료 시각까지 유효합니다.

이 정책은 탈취 후 재사용을 엄격하게 막지만, 사용자의 실수나 네트워크 재시도만으로도 해당 로그인 세션이 종료될 수 있습니다. 실제 PostgreSQL에서 두 트랜잭션으로 동일 Token의 동시 갱신을 실행하여 위 결과를 검증합니다.

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

## 결정

MVP에서는 **대안 1: 현재 구조와 엄격한 재사용 폐기 유지**를 선택합니다.

- 현재 요구사항은 로그인, 갱신과 로그아웃이며 계정별 세션 목록이나 기기별 로그아웃은 포함하지 않습니다.
- 실제 PostgreSQL 동시성 테스트에서 Account와 동일 Token의 행 잠금, 단일 갱신과 family 전체 폐기를 확인했습니다.
- 고정 세션 행을 추가해도 정상 중복 요청과 탈취자의 재사용을 구분할 수는 없습니다.
- `refresh_sessions`를 도입하면 스키마, Entity, 조회와 잠금 경로가 늘어나며 현재 팀이 감당할 유지보수 비용이 커집니다.

MVP에서는 다음 결과와 한계를 수용합니다.

- 동일 Refresh Token의 동시 요청 중 하나가 갱신된 뒤 다른 요청이 재사용으로 판단되면 해당 family를 폐기합니다.
- 브라우저 중복 요청이나 네트워크 재시도도 다시 로그인을 요구할 수 있습니다.
- 별도 고정 Session 행은 사용하지 않지만, 서로 다른 세대의 Token 요청도 Account 행을 공통 기준으로 직렬화합니다.
- 폐기된 family에서 이미 발급한 Access JWT는 별도 denylist가 없으므로 만료 시각까지 유효합니다.
- 회전할 때마다 Token 행이 쌓이므로 보존·삭제 정책은 운영 단계에서 별도로 정합니다.

MVP에서는 `refresh_sessions`, Redis와 BFF를 추가하지 않습니다. 다음 요구나 문제가 실제로 확인되면 인증 경계와 함께 다시 판단합니다.

- 계정별 또는 기기별 세션 조회와 개별 로그아웃
- 실제 운영에서 반복되는 갱신 충돌
- BFF와 서버 세션 도입 결정
- Refresh Token 보존·삭제 정책 수립

스키마 변경이 필요해지면 기존 V1·V2 Migration을 수정하지 않고 새 Flyway Migration과 회귀 테스트를 추가합니다.

## 관련 문서

- 중앙 ADR `0003`: 브라우저 직접 JWT 인증 경계
- 중앙 ADR `0008`: 계정 UUID와 JWT Subject 식별자
- 중앙 인증·인가 명세: `10-specifications/01-identity/02-authentication-authorization.md`
- 구현: `AuthenticationService`, `RefreshTokenRotation`, `RefreshTokenRepository`, `RefreshTokenJpaRepository`
