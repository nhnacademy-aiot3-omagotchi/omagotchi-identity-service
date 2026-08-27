# ADR 0002: 계정 인증·Refresh Session 직렬화

- 상태: Accepted
- 작성일: 2026-08-24
- 결정일: 2026-08-24

## 배경

Identity Service는 로그인 단위의 Refresh Token family를 여러 개 허용합니다. 로그인 실패 잠금은
같은 계정의 연속 실패 횟수를 유실 없이 갱신해야 하고, 비밀번호 변경·재설정·계정 비활성화·탈퇴는
사용자 ID를 기준으로 모든 Refresh Token family를 폐기해야 합니다.

기존 요청 Token 행만 먼저 잠그는 방식과 계정별 전체 폐기를 함께 사용하면 다음 문제가 생깁니다.

- 두 로그인 실패가 같은 실패 횟수를 읽고 각각 같은 값으로 갱신할 수 있습니다.
- Refresh 회전이 전체 폐기와 교차하면 폐기 일괄 갱신 뒤 새 후속 Token이 저장될 수 있습니다.
- Refresh가 Token 행을 잡고 계정을 기다리는 동안 전체 폐기가 계정을 잡고 Token 갱신을 기다리면
  잠금 순서가 반대가 됩니다.
- 사용된 과거 Token과 현재 Token처럼 서로 다른 세대의 요청은 Token 행 잠금만으로 같은 family의
  변경을 직렬화하지 못합니다.

## 결정

`accounts` 행을 사용자 단위 인증 상태와 Refresh Session 변경의 공통 직렬화 지점으로 사용합니다.

```text
비밀번호 로그인
  → email로 Account SELECT FOR UPDATE
  → 잠금 만료 복구·자격 증명 검증·실패 횟수 변경
  → 성공 시 새 Refresh Token 저장

Refresh·Logout
  → token_hash로 account_id 값만 조회
  → Account SELECT FOR UPDATE
  → 같은 token_hash를 RefreshToken SELECT FOR UPDATE로 다시 조회·검증
  → 회전 또는 family 폐기

사용자 전체 Refresh Session 폐기
  → Account SELECT FOR UPDATE
  → account_id의 모든 미폐기 RefreshToken 일괄 폐기
```

- 잠금 순서는 항상 `Account → RefreshToken`으로 고정합니다.
- 최초 `token_hash → account_id` 조회는 JPA Entity가 아닌 UUID 값만 반환합니다.
- 계정 행을 잠근 뒤 Token 행을 다시 잠가 만료·사용·폐기 상태를 최종 판단합니다.
- 사용자 전체 폐기는 이미 폐기된 행을 변경하지 않는 멱등 동작입니다.
- 로그인 실패는 기존 Refresh Token을 폐기하지 않습니다.
- `LOCKED` 계정도 기존 Refresh Token 회전을 허용합니다.
- `DISABLED`, `WITHDRAWN` 계정은 기존 정책대로 Refresh를 거부합니다.
- Access JWT는 denylist를 두지 않으므로 기존 `exp`까지 유효합니다.

로그인 실패 횟수는 인증 실패 응답보다 먼저 Commit되어야 합니다. 이를 위해 `LoginTransaction`은
실패를 `Optional.empty()`로 정상 반환하고, 바깥 `AuthenticationService`가 Transaction 종료 뒤
`AUTH_INVALID_CREDENTIALS`의 `BusinessException`으로 변환합니다. Token 발급 중 예상하지 못한 실패는
원본 예외를 전파하여 계정 상태 변경과 Token 저장을 함께 Rollback합니다.

## 설정

로그인 잠금 정책은 다음 필수 환경변수로 주입합니다.

- `LOGIN_MAXIMUM_FAILED_ATTEMPTS`: 1~32767
- `LOGIN_LOCK_DURATION`: 1초 이상의 ISO-8601 Duration

애플리케이션 설정에 기본값을 두지 않습니다. 값 누락·형식·범위 오류는 시작 실패로 처리합니다.
현재 운영 정책으로 주입할 값은 5회와 `PT10M`입니다.

## 결과와 한계

- 같은 사용자의 로그인·Refresh·Logout·전체 폐기는 직렬화됩니다.
- 서로 다른 사용자의 인증 변경은 서로 다른 계정 행을 사용하므로 병렬 처리됩니다.
- BCrypt 검증과 Token 발급 동안 해당 계정 행 잠금을 유지해 같은 계정의 인증 요청 처리량은
  의도적으로 제한됩니다.
- 존재하지 않는 이메일은 잠글 계정 행이 없으므로 fallback BCrypt 비교만 수행합니다.
- 계정 단위 요청 제한과 IP 단위 Rate Limit은 이 결정에 포함하지 않습니다.
- 새로운 `refresh_sessions` 테이블과 Redis 분산 잠금은 추가하지 않습니다.

## 검증

- 로그인 실패 응답 뒤 실패 횟수 Commit과 5회 잠금
- 성공 로그인 실패 횟수 초기화와 만료 잠금 복구
- 동시 로그인 실패의 횟수 유실 방지
- 잠긴 계정의 기존 Refresh 허용과 로그인 실패에 의한 Token 비폐기
- 복수 family와 회전 세대를 포함한 사용자 전체 폐기
- Refresh 회전과 사용자 전체 폐기의 직렬화 및 후속 Token 폐기
- 다른 사용자의 Refresh Token 비영향

## 관련 문서

- [ADR 0001 Refresh Token 회전 동시성 정책](0001-refresh-token-rotation-concurrency.md)
- 중앙 인증·인가 명세 `10-specifications/01-identity/02-authentication-authorization.md`
- 중앙 사용자 계정 명세 `10-specifications/01-identity/01-account.md`
