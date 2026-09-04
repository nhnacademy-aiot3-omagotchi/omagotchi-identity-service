# ADR 0003: PostgreSQL 기반 이메일 인증 경계

- 상태: Proposed
- 작성일: 2026-08-30

## 배경

이메일 인증은 인증번호 상태, 재발급 쿨다운, 실패 횟수, 회원가입·로그인 사용자 비밀번호 변경·
비로그인 사용자 비밀번호 재설정과 메일 사업자 호출을 함께 다룹니다. 서로 다른 저장소와 비동기
실행기를 하나의 업무처럼 묶으면 다음 문제가 생깁니다.

- PostgreSQL Transaction이 Rollback되어도 Redis 쓰기와 이미 전송된 메일은 되돌릴 수 없습니다.
- 쿨다운 키를 선점한 실행기가 실패했을 때 키 삭제 여부와 시점에 따라 중복 발송 또는 장시간 차단이 생깁니다.
- 요청 Thread와 비동기 실행기의 성공 기준이 달라 API가 실제 발송 결과를 설명하지 못합니다.
- 동일 이메일의 동시 재발급과 인증 시도에서 상태 전이가 여러 저장소에 나뉘면 원자적 검증이 어렵습니다.

## 결정

첫 구현은 PostgreSQL을 이메일 인증 상태의 유일한 저장소로 사용하고, 메일은 요청 Thread에서 동기로
호출합니다. Redis, `@Async`, 별도 Executor, Outbox는 사용하지 않습니다.

인증 용도는 `SIGNUP`, `PASSWORD_CHANGE`, `PASSWORD_RESET`으로 분리합니다. Challenge는 이메일과
용도에 묶인 일회성 상태이며 다른 용도로 재사용할 수 없습니다. 발송 쿨다운은 용도별 인증 상태와
분리해 정규화 이메일 전체가 공유합니다.

### API 계약

기존 v1 API는 변경하지 않고 다음 v2 API를 추가합니다.

| API | 인증 | 성공 |
|---|---|---|
| `POST /api/v2/auth/signup/email-otp` | Frontend Basic | `202 Accepted` |
| `POST /api/v2/auth/signup` | Frontend Basic | `201 Created` |
| `POST /api/v2/users/me/password/email-otp` | Bearer JWT | `202 Accepted` |
| `PATCH /api/v2/users/me/password` | Bearer JWT | `204 No Content` |
| `POST /api/v2/auth/password-reset/email-otp` | Frontend Basic | `202 Accepted` |
| `PATCH /api/v2/auth/password-reset` | Frontend Basic | `204 No Content` |

발급 API는 `challengeId`와 만료까지 남은 초를 반환합니다. 인증 API는 이메일·용도·`challengeId`·
인증번호가 모두 일치해야 합니다. 쿨다운 중 요청은 `429 Too Many Requests`와 `Retry-After`를,
메일 사업자 실패는 `503 Service Unavailable`을 반환합니다.

비밀번호 재설정은 사용자 Bearer JWT와 현재 비밀번호를 요구하지 않지만, Browser가 Identity를 직접
호출하지 않도록 Frontend Basic 경계에 둡니다. 문법상 유효한 이메일에는 계정 존재·상태를 먼저
조회하지 않고 `PASSWORD_RESET` Challenge와 OTP 메일을 발급합니다. 따라서 미가입 주소에도 OTP가
전달될 수 있지만 발급 응답과 메일 호출 시간으로 계정 존재 여부를 구분할 수 없습니다. 실제 계정
존재와 재설정 가능 상태는 OTP를 제출한 최종 Transaction에서 확인하며, 존재하지 않는 계정·허용되지
않은 상태·문법상 올바르지만 유효하지 않은 Challenge는 하나의 공개 오류로 변환합니다. JSON
역직렬화와 Bean Validation에서 거절되는 요청 형식 오류는 기존 공통 오류 계약을 유지합니다.

OTP 확인 후 임시 비밀번호를 이메일로 보내지 않습니다. 사용자가 제출한 새 비밀번호로 직접 변경하고,
완료 알림 메일에는 비밀번호를 포함하지 않습니다. `ACTIVE`와 로그인 실패로 인한 `LOCKED` 계정만
재설정하며, 성공 시 로그인 실패 상태를 초기화하고 모든 Refresh Session을 `PASSWORD_RESET` 사유로
폐기합니다. 자동 로그인은 하지 않습니다. 이미 발급된 Access Token은 별도 즉시 폐기 수단이 없으므로
설정된 TTL까지 남을 수 있습니다.

### 저장 모델

`email_delivery_cooldowns`는 정규화 이메일 전체의 발송 쿨다운 직렬화 지점입니다.
`email_verification_scopes`는 정규화 이메일과 용도 조합별 현재 Challenge의 직렬화 지점입니다.
발급은 `EmailDeliveryCooldown → EmailVerificationScope → Challenge` 순서로 행을
`SELECT FOR UPDATE` 잠급니다. 행이 없을 때는 `INSERT ... ON CONFLICT DO NOTHING`으로 생성한 후
잠그므로 최초 동시 요청도 같은 기준으로 직렬화됩니다.

`email_verification_challenges`는 다음 상태를 저장합니다.

- 인증 상태: `OPEN`, `CONSUMED`, `EXHAUSTED`, `SUPERSEDED`
- 전달 상태: `PENDING`, `ACCEPTED`, `FAILED`
- 만료 시각, 실패 횟수, 인증번호 MAC

인증 상태와 전달 상태를 분리해 “메일 전달은 실패했지만 인증번호 상태는 무엇인가”를 모호하지 않게
표현합니다. 만료는 별도 Scheduler로 행을 갱신하지 않고 `expires_at`과 현재 시각으로 판단합니다.

인증번호 원문은 저장하지 않습니다. 서버 비밀값을 사용하는 HMAC-SHA256으로
`challengeId + 이메일 + 용도 + 인증번호`를 서명하고 상수 시간 비교를 사용합니다.

### 발급 흐름과 보상

```text
요청 검증
  → PostgreSQL Transaction
      EmailDeliveryCooldown 생성·잠금
      이메일 전체 공유 쿨다운 검사
      Scope 생성·잠금
      이전 OPEN Challenge를 SUPERSEDED로 전이
      새 Challenge(PENDING)와 공유 다음 발급 가능 시각 저장
    Commit
  → Resend 동기 호출
  → 성공: 전달 상태 ACCEPTED 기록, 202
  → 실패: 전달 상태 FAILED 기록 + 현재 Challenge이면 즉시 재발급 가능하게 보상, 503
```

DB Commit 전에는 메일을 보내지 않습니다. Commit 뒤 메일 전송은 되돌릴 수 없으므로 DB 행을 삭제해
Rollback을 흉내 내지 않습니다. 대신 전달 실패를 기록하고, 같은 Challenge가 여전히 현재 발급 건일
때만 자신이 예약한 공유 쿨다운을 해제합니다. 오래 걸린 과거 요청이나 다른 용도 요청이 최신 발급의
공유 쿨다운을 해제할 수 없습니다.

동기 호출이 요청 Thread와 Challenge 수명을 무기한 점유하지 않도록 Resend 연결 시간과 전체 응답
시간에 명시적인 상한을 둡니다. `connect timeout ≤ read timeout < OTP TTL` 관계를 기동 시 검증합니다.
시간 제한이나 네트워크 실패는 전달 실패와 동일하게 `FAILED` 기록·쿨다운 보상·`503`으로 처리합니다.
사업자 성공 응답 뒤 상태 기록까지 지연되어 Challenge가 만료된 경우에는 전달 상태를 되돌리지 않고
현재 Challenge의 쿨다운만 해제한 뒤 `503`을 반환합니다. 따라서 만료된 Challenge로 `202`를 응답하지
않습니다.

네트워크 시간 제한은 사업자가 요청을 접수하지 않았다는 증거가 아닙니다. 시간 제한 뒤 도착한 메일과
재시도로 발급한 메일이 모두 전달될 수 있지만, 새 발급은 이전 Challenge를 `SUPERSEDED`로 전이하므로
최신 메일의 인증번호만 유효합니다. 이 불확실성 자체를 제거하려면 사업자 Webhook 또는 Outbox·Worker
기반 상태 확인이 필요하며 첫 구현 범위에는 포함하지 않습니다.

메일은 전송되었지만 성공 기록만 실패하는 불확실성에 대비해 `challengeId`를 메일 사업자
idempotency key로 사용합니다. 전달 상태가 `PENDING`이어도 올바른 Challenge 검증은 허용합니다.
전달 상태는 관측·보상 정보이며 인증의 진실 공급원이 아닙니다.

### 인증과 업무 Transaction

인증 실패 횟수는 실패 응답과 함께 Rollback되면 안 됩니다. 따라서 Transaction 내부 작업은 예상 가능한
인증 실패를 결과값으로 반환하고, 바깥 Service가 Commit 뒤 공개 예외로 변환합니다.

인증 성공 시 회원가입, 로그인 사용자의 비밀번호 변경 또는 비로그인 사용자의 비밀번호 재설정과
Challenge `CONSUMED` 전이는 하나의 PostgreSQL Transaction으로 처리합니다. 계정 저장, 비밀번호
변경, 로그인 실패 상태 초기화, Refresh Session 폐기 중 하나라도 기술적으로 실패하면 Challenge 소비도
함께 Rollback됩니다. 존재하지 않거나 재설정할 수 없는 계정에 올바른 OTP가 제출된 경우에는 이후
생성·활성화된 계정에 OTP가 재사용되지 않도록 Challenge를 소비한 뒤 일반화된 오류를 반환합니다.

Refresh Session 동시성은 [ADR 0002](0002-account-authentication-refresh-session-serialization.md)를
따릅니다. `accounts` 행을 사용자 단위 인증 상태와 Refresh Session 변경의 공통 직렬화 지점으로
사용하고, 잠금 순서는 항상 `Account → RefreshToken`으로 고정합니다. Refresh·Logout의 최초
`token_hash → account_id` 조회는 UUID 값만 반환하며, 계정 행을 잠근 뒤 요청 Token 행을 잠가 최종
상태를 판단합니다. 사용자 전체 폐기는 미폐기 RefreshToken만 변경하므로 이미 폐기된 행에는 영향을
주지 않는 멱등 동작입니다.

비밀번호 재설정은 `Account → Challenge → RefreshToken` 순서로 잠급니다. `ACTIVE`·`LOCKED`가 아닌
계정과 존재하지 않는 계정은 같은 실패 결과로 처리하고, `LOCKED` 재설정 성공은 로그인 실패 횟수와
잠금 시각을 초기화해 `ACTIVE`로 복구합니다.

```text
Account SELECT FOR UPDATE
  → Challenge SELECT FOR UPDATE
  → 잘못된 번호: 실패 횟수 증가 후 정상 반환 → Commit → 바깥에서 400
  → 올바른 번호: 업무 변경 → Challenge CONSUMED → Commit
  → 계정 부재·비허용 + 올바른 번호: Challenge CONSUMED → Commit → 바깥에서 400
  → 업무 기술 실패: 전체 Rollback
```

### 정책 설정

운영값은 코드 기본값 없이 환경변수로 주입합니다.

- 인증번호: 숫자 6자리
- 유효시간: `EMAIL_VERIFICATION_CODE_TTL` (`PT5M` 권장)
- 이메일 전체 공유 재발급 쿨다운: `EMAIL_VERIFICATION_COOLDOWN` (`PT1M` 권장)
- 최대 실패 횟수: `EMAIL_VERIFICATION_MAXIMUM_FAILED_ATTEMPTS` (`5` 권장)
- HMAC 비밀값: `EMAIL_VERIFICATION_HMAC_SECRET` (최소 32자)
- Resend API key와 발신 주소: `RESEND_API_KEY`, `RESEND_FROM_EMAIL`
- Resend 연결·전체 응답 시간 상한: `RESEND_CONNECT_TIMEOUT` (`PT2S` 권장),
  `RESEND_READ_TIMEOUT` (`PT5S` 권장)

## 제외 범위

- 대규모 발송량을 위한 Outbox·Queue·Worker
- Redis 기반 IP·디바이스 Rate Limit
- 메일 사업자 Webhook에 의한 최종 전달 확인
- 자동 Retry와 다중 메일 사업자 Failover
- CAPTCHA 구현. 공개 비밀번호 재설정 OTP 발급 BFF에 후속 TODO로만 남김

필요한 처리량과 장애 요구가 확인되면 Outbox를 별도 ADR로 추가합니다. 그때도 인증 상태의 진실
공급원은 PostgreSQL로 유지합니다.

## 검증 기준

- 같은 이메일의 같은 용도와 서로 다른 용도 동시 발급에서 한 요청만 공유 쿨다운을 통과한다.
- 메일 실패 후 최신 Challenge만 재발급 쿨다운을 해제한다.
- 잘못된 번호의 실패 횟수는 오류 응답 후에도 보존되고 최대 횟수에서 소진된다.
- 회원가입·비밀번호 변경의 업무 실패와 재설정 가능한 계정의 비밀번호 재설정 기술 실패 시 Challenge
  소비가 Rollback된다.
- 계정 부재·비허용 상태에 올바른 비밀번호 재설정 OTP가 제출되면 Challenge는 소비된다.
- 성공한 Challenge는 한 번만 소비되며 다른 이메일·용도로 재사용할 수 없다.
- 인증번호 원문과 HMAC 비밀값이 로그·응답·DB에 노출되지 않는다.
- Resend 지연 응답은 설정한 시간 안에 실패하고 만료된 Challenge에는 `202`를 반환하지 않는다.
- 비밀번호 재설정 OTP 발급은 Account를 조회하지 않고 등록·미등록 이메일에 같은 외부 계약을 제공한다.
- `LOCKED` 계정 재설정은 로그인 실패 상태를 초기화하고 Refresh Session을 모두 폐기한다.

## 결과와 한계

상태 전이와 업무 변경은 PostgreSQL Transaction으로 설명할 수 있고, 외부 메일 호출은 명시적인
보상 경계로 분리됩니다. API 응답 시간은 메일 사업자 지연의 영향을 받지만 명시한 read timeout을
넘지 않습니다. 초기 기능의 정확성과 운영 가능성을 우선한 선택이며, 처리량 요구가 생기기 전까지
유지합니다.

비밀번호 재설정의 단계별 적용과 검토 게이트는
[비밀번호 재설정 구현 계획](../password-reset-implementation-plan.md)을 따릅니다.
