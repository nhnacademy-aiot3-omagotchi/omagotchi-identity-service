# 비밀번호 재설정 구현 계획

- 상태: Identity API 구현 완료 (단계 0~8)
- 작성일: 2026-09-03
- 현재 적용 대상: Identity 비밀번호 재설정 API
- 후속 범위: Frontend BFF와 공개 재설정 화면 (단계 9~11)

## 1. 진행 원칙

- 이 문서를 구현 순서와 검토 범위의 기준으로 사용한다.
- 한 번에 한 단계만 변경하고, 각 단계의 검증 결과와 diff를 사용자에게 제시한다.
- 사용자 승인을 받기 전에는 다음 단계로 넘어가지 않는다.
- 기존 변경을 보존하고 작업 내용을 직접 stage 또는 commit하지 않는다.
- CAPTCHA는 구현하지 않고 공개 OTP 발급 BFF의 후속 `TODO`로만 남긴다.

## 2. 용어와 기능 경계

### 비밀번호 재설정

- 현재 비밀번호와 사용자 Bearer JWT를 사용할 수 없는 비로그인 계정 복구 흐름이다.
- 이메일 OTP로 계정 통제권을 확인한 뒤 사용자가 제출한 새 비밀번호로 변경한다.
- 인증 용도는 `PASSWORD_RESET`이며 회원가입 `SIGNUP`, 로그인 사용자의 비밀번호 변경
  `PASSWORD_CHANGE`와 Challenge를 공유하지 않는다.

### 로그인 사용자의 비밀번호 변경

- 기존 `/api/v2/users/me/password/**` 흐름을 유지한다.
- Bearer JWT와 현재 비밀번호 등 기존 계약을 비밀번호 재설정에 재사용하지 않는다.

## 3. 확정 계약

### 3.1 임시 비밀번호를 발급하지 않는다

OTP가 맞으면 서버가 임시 비밀번호를 생성해 이메일로 보내는 대신, 사용자가 OTP와 새 비밀번호를
함께 제출한다. 최종 Transaction에서 다음 작업을 원자적으로 수행한다.

1. 계정과 Challenge를 잠그고 재설정 가능 여부와 OTP를 확인한다.
2. 새 비밀번호를 해시해 계정에 저장한다.
3. 로그인 실패 횟수와 잠금 시각을 초기화한다.
4. 모든 Refresh Session을 `PASSWORD_RESET` 사유로 폐기한다.
5. Challenge를 `CONSUMED`로 전이한다.

계정이 재설정 가능한 상태에서 비밀번호 변경, Refresh Session 폐기 또는 Challenge 소비 중 기술 실패가
발생하면 전체 작업을 Rollback한다. 반면 존재하지 않거나 재설정할 수 없는 계정에 올바른 OTP가 제출된
경우에는 이후 생성·활성화된 계정에 OTP가 재사용되지 않도록 Challenge를 소비한 뒤 일반화된 오류를
반환한다. 완료 알림 메일이 필요하면 비밀번호를 포함하지 않고 Commit 뒤 발송하며, 발송 실패로 이미
완료된 재설정을 되돌리지 않는다.

### 3.2 인증 경계

```text
Browser -- anonymous + CSRF --> Frontend BFF -- Frontend Basic --> Identity
```

- Browser의 비밀번호 재설정 API에는 로그인 세션이나 사용자 JWT를 요구하지 않는다.
- 상태 변경 요청이므로 Frontend BFF의 CSRF 보호는 유지한다.
- Browser가 Identity를 직접 호출하지 않으며 BFF가 기존 Frontend Basic 자격으로 호출한다.
- CAPTCHA는 이 BFF 진입점에 `TODO`만 남긴다.

### 3.3 계정 존재 여부 비공개

- OTP 발급은 문법상 유효한 이메일이면 Account를 먼저 조회하지 않는다.
- 미가입 이메일에도 `PASSWORD_RESET` Challenge를 만들고 같은 메일 발송 흐름을 수행한다.
- 발급 응답 상태, 본문, 메일 호출 여부와 가능한 범위의 처리 시간이 계정 존재 여부에 따라 달라지지
  않게 한다.
- 실제 계정 존재 여부와 상태는 OTP를 제출하는 최종 Transaction에서 확인한다.
- 존재하지 않는 계정, 재설정 불가 상태, 문법상 올바르지만 유효하지 않은 Challenge·OTP는 공개 API에서
  하나의 일반화된 오류로 변환한다.
- JSON 역직렬화 실패와 Bean Validation 실패는 Application 진입 전의 요청 형식 오류이므로 기존 공통
  오류 계약을 유지한다.

미가입 주소에 OTP 메일이 전달되는 비용은 계정 열거 방지를 위해 수용한다. IP·디바이스 기반 방어와
CAPTCHA는 이번 범위에 포함하지 않는다.

### 3.4 이메일 발송 쿨다운

- 쿨다운은 정규화 이메일 전체가 공유한다. `SIGNUP`, `PASSWORD_CHANGE`, `PASSWORD_RESET`별로
  따로 우회할 수 없다.
- Challenge 상태와 용도 격리는 `(email, purpose)` Scope가 담당한다.
- 이메일 전체 발송 직렬화와 다음 발송 가능 시각은 별도 `email_delivery_cooldowns`가 담당한다.
- 기존 설정 `EMAIL_VERIFICATION_COOLDOWN`을 공유하며 신규 운영 설정을 추가하지 않는다.

발급 시 잠금 순서는 다음과 같이 고정한다.

```text
EmailDeliveryCooldown → EmailVerificationScope → Challenge
```

최초 동시 요청도 같은 행에서 직렬화되도록 행이 없을 때 `INSERT ... ON CONFLICT DO NOTHING`으로
생성한 다음 `SELECT FOR UPDATE`로 잠근다. 메일 전달 실패 보상은 해당 요청이 소유한 최신 발송 예약과
현재 Challenge일 때만 공유 쿨다운을 해제한다.

### 3.5 재설정 대상과 세션

- `ACTIVE` 계정과 로그인 실패로 잠긴 `LOCKED` 계정만 재설정할 수 있다.
- `LOCKED` 계정의 재설정 성공 시 상태를 `ACTIVE`로 바꾸고 로그인 실패 횟수와 잠금 시각을 초기화한다.
- `DISABLED`, `WITHDRAWN` 등 그 밖의 상태는 재설정하지 않는다.
- 성공 시 모든 Refresh Session을 `PASSWORD_RESET` 사유로 폐기한다.
- 자동 로그인이나 새 토큰 발급은 하지 않는다.
- 기존 Access Token은 즉시 폐기 저장소가 없으므로 설정된 TTL까지 남을 수 있음을 명시한다.

최종 재설정 잠금 순서는 다음과 같이 고정한다.

```text
Account → Challenge → RefreshToken
```

### 3.6 API 계약

Identity에 다음 API를 추가한다.

| API | 인증 | 성공 |
|---|---|---|
| `POST /api/v2/auth/password-reset/email-otp` | Frontend Basic | `202 Accepted` |
| `PATCH /api/v2/auth/password-reset` | Frontend Basic | `204 No Content` |

Frontend BFF에도 같은 공개 경로를 제공한다.

OTP 발급 요청은 이메일을 받고, 성공 응답은 기존 v2 발급 응답 계약인 `challengeId`와 만료까지 남은
초를 사용한다. 최종 요청은 이메일, `challengeId`, OTP, 새 비밀번호를 받는다. 새 비밀번호 정책은
기존 비밀번호 변경 정책과 동일하게 재사용한다.

쿨다운은 `429 Too Many Requests`와 `Retry-After`, 메일 사업자 실패는 `503 Service Unavailable`을
사용한다. 최종 재설정의 계정·Challenge 관련 공개 실패는 계정 존재나 상태를 구분하지 않는 하나의
오류 코드로 제공한다.

## 4. 단계별 구현과 검토 게이트

### 단계 0. 계획 파일 저장

- 변경: 이 구현 계획을 저장한다.
- 검증: 문서 링크와 단계·비범위·검토 규칙을 확인한다.
- 완료 조건: 사용자 승인.

### 단계 1. ADR과 공개 계약 확정

- 변경:
  - ADR 0003에 `PASSWORD_RESET`, 공유 이메일 쿨다운, 비밀번호 재설정 Transaction을 기록
  - Identity API, 인증 경계, 계정 열거 방지, 재설정 가능 상태, 세션 폐기, CAPTCHA 비범위를 기록
  - 이 계획 문서를 ADR에서 연결
- 비변경: 운영 코드, 테스트, Migration
- 검증: `git diff --check`, 문서 링크와 계약 문구 확인
- 완료 조건: 사용자 승인.

### 단계 2. PASSWORD_RESET 용도와 DB 제약 확장

- 변경:
  - `EmailVerificationPurpose.PASSWORD_RESET` 추가
  - 기존 V5를 수정하지 않고 maintenance 단계의 V9 Migration에서 Scope·Challenge purpose CHECK 제약 갱신
- 비변경: 발급 흐름, Controller, Account
- 집중 검증:
  - Purpose 격리 Domain/Application 테스트
  - Flyway Migration 이력 기대값 테스트
- 완료 조건: 사용자 승인.

### 단계 3. 이메일 전체 공유 쿨다운 저장 모델

- 변경:
  - V8 Migration에서 `email_delivery_cooldowns` 추가·기존 Scope 쿨다운 사전 백필
  - OTP 트래픽을 중지한 maintenance 단계의 V9 Migration에서 최종 백필, purpose CHECK 갱신 후
    기존 `next_issue_at` 제약·컬럼 제거
  - 공유 쿨다운 Domain, Repository port, JPA adapter 추가
  - 생성 후 잠금과 발송 예약 소유권 계약 추가
- 비변경: 기존 발급 서비스 연결
- 집중 검증:
  - 정규화 이메일당 한 행
  - 다음 발급 시각 전 거절
  - 최초 동시 생성 직렬화
  - 용도별 Scope 독립성
  - V9가 5초 안에 배타 잠금을 얻지 못하면 전체 Rollback하고 blocker 확인 후 재시도
- 완료 조건: 사용자 승인.

운영 적용 순서는 다음과 같다.

1. 구버전 인스턴스를 유지한 채 `spring.flyway.target=8`로 V8까지만 사전 적용한다.
2. OTP 트래픽을 차단하고 구버전 인스턴스의 진행 중 Transaction이 끝날 때까지 기다린다.
3. V9를 적용하고 새 버전 인스턴스를 기동한 뒤 OTP 트래픽을 재개한다.

V8과 V9 사이에는 구버전만 실행한다. 구버전은 Scope의 `next_issue_at`, 새 버전은
`email_delivery_cooldowns`를 사용하므로 두 버전을 혼합 운영하면 공유 쿨다운이 서로 달라질 수 있다.
Flyway target을 제한하지 않는 기본 기동은 V9까지 연속 적용하므로 처음부터 maintenance 단계에서 수행한다.

V9 재시도 중에는 OTP 트래픽 중지 상태를 유지한다. 잠금 실패 시 5초, 15초 간격으로 최대 두 번
다시 실행하며, 총 세 번 실패하면 배포를 중단하고 장기 Transaction과 `idle in transaction` 세션을
확인한다. PostgreSQL Transaction Rollback이 확인되지 않은 상태에서 `flyway repair`를 실행하지 않는다.

### 단계 4. 기존 이메일 발급 흐름에 공유 쿨다운 연결

- 변경:
  - 발급 Transaction의 잠금 순서를
    `EmailDeliveryCooldown → EmailVerificationScope → Challenge`로 변경
  - 메일 성공·실패 보상 시 공유 쿨다운 소유권을 확인
  - `SIGNUP`, `PASSWORD_CHANGE`, `PASSWORD_RESET`이 같은 쿨다운을 사용
- 비변경: 공개 비밀번호 재설정 Controller
- 집중 검증:
  - 서로 다른 용도의 동시 발급 중 한 요청만 통과
  - 최신 발급만 실패 보상 가능
  - 기존 회원가입·비밀번호 변경 회귀 테스트
- 완료 조건: 사용자 승인.

### 단계 5. 계정 비밀번호 재설정 Domain/Application 계약

- 변경:
  - 재설정 가능 계정 상태 판정과 `LOCKED → ACTIVE` 복구
  - 비밀번호 정책과 해시 교체 재사용
  - 로그인 실패 상태 초기화
  - 기존 Refresh Session `PASSWORD_RESET` 폐기 사유와 매핑 재사용·검증
- 비변경: HTTP Controller, OTP 최종 Transaction 조립
- 집중 검증:
  - `ACTIVE`, `LOCKED` 성공
  - 그 밖의 상태 거절
  - 비밀번호 재사용·정책 위반 거절
  - 로그인 잠금 상태 초기화와 폐기 사유 매핑
- 완료 조건: 사용자 승인.

### 단계 6. OTP와 계정 변경을 묶는 재설정 Transaction

- 변경:
  - Account를 먼저 잠근 뒤 `PASSWORD_RESET` Challenge 검증·소비
  - 비밀번호 변경, 로그인 실패 초기화, Refresh Session 폐기를 하나의 Transaction으로 처리
  - 예상 가능한 실패는 Commit 뒤 일반화된 공개 오류로 변환
  - 계정 부재·비허용 상태의 올바른 OTP는 소비한 뒤 일반화된 공개 오류로 변환
- 비변경: HTTP Controller
- 집중 검증:
  - 잘못된 OTP의 실패 횟수 보존
  - 기술 실패 시 비밀번호와 Challenge 소비 Rollback
  - 동일 Challenge 한 번만 성공
  - 잠금 순서 검증
- 완료 조건: 사용자 승인.

### 단계 7. Identity HTTP·Security 경계

- 변경:
  - `POST /api/v2/auth/password-reset/email-otp`
  - `PATCH /api/v2/auth/password-reset`
  - Frontend Basic 인가, 요청 검증, 응답·오류 매핑
  - OTP 발급 시 Account 선조회 금지
- 비변경: Frontend BFF와 화면
- 집중 검증:
  - Basic 없음·오류 자격 거절
  - 사용자 JWT 없이 성공
  - 등록·미등록 이메일의 같은 발급 응답과 메일 호출
  - 일반화된 최종 오류
- 완료 조건: 사용자 승인.

### 단계 8. Identity 통합·동시성 검증

- 변경: 운영 코드 변경 없이 IT와 회귀 검증 보강
- 집중 검증:
  - 실제 PostgreSQL Migration과 CHECK 제약
  - 같은 이메일의 목적 교차 동시 발급
  - 같은 Challenge의 동시 재설정
  - 재설정 성공 시 로그인·Refresh Session 동작
  - 기존 회원가입·비밀번호 변경 회귀
- 완료 조건: 사용자 승인.

### 단계 9. Frontend BFF 전송 계층

- 변경:
  - 공개 BFF Controller와 Identity HTTP client 추가
  - CSRF 유지, 사용자 JWT 비요구, Frontend Basic 전달
  - `Retry-After`와 공개 오류 보존
  - OTP 발급 진입점에 CAPTCHA 후속 `TODO` 추가
- 비변경: CAPTCHA 구현, 화면
- 집중 검증:
  - 익명 GET/POST 경계와 CSRF
  - 헤더·상태·본문 전달
  - 로그인 세션 없는 요청
- 완료 조건: 사용자 승인.

### 단계 10. 비밀번호 재설정 화면

- 변경:
  - 이메일 입력과 OTP 발급
  - OTP, 새 비밀번호, 새 비밀번호 확인 제출
  - 쿨다운과 오류 표시, 성공 후 로그인 이동
- 비변경: 자동 로그인, 임시 비밀번호 표시
- 집중 검증:
  - 등록 여부를 드러내지 않는 문구
  - 쿨다운 카운트다운
  - CSRF 포함 실제 BFF 호출
  - 접근성 및 브라우저 흐름
- 완료 조건: 사용자 승인.

### 단계 11. 문서와 전체 검증 마감

- 변경:
  - API·운영 설정·보안 한계 문서 갱신
  - 구현과 검증이 끝난 ADR 상태 재평가
- 검증:
  - Identity 단위·통합·동시성 전체 검증
  - Frontend 단위·통합·브라우저 검증
  - Migration 이력과 깨끗한 환경 기동
  - `git diff --check`와 최종 변경 범위 확인
- 완료 조건: 사용자 승인 후 구현 종료.

## 5. 매 단계 보고 형식

각 단계가 끝나면 다음 항목만 보고하고 다음 승인을 기다린다.

1. 이번 단계에서 변경한 파일과 계약
2. 실행한 검증 명령과 결과
3. 아직 구현하지 않은 다음 단계 범위
4. 직접 commit하지 않았음을 명시
5. 변경점이 포함된 한국어 권장 commit message

## 6. 전체 완료 기준

- 비로그인 사용자가 이메일 OTP와 직접 정한 새 비밀번호로 계정을 복구할 수 있다.
- 계정 존재 여부가 발급 응답·메일 호출·최종 오류에서 노출되지 않는다.
- 세 인증 용도가 Challenge를 공유하지 않고 이메일 발송 쿨다운은 공유한다.
- 비밀번호 변경과 Challenge 소비, 로그인 실패 초기화, Refresh Session 폐기가 원자적이다.
- 임시 비밀번호나 OTP 원문, HMAC 비밀값이 저장·로그·응답·메일에 노출되지 않는다.
- CAPTCHA는 구현되지 않고 지정한 BFF 진입점의 후속 `TODO`로만 남는다.
- 각 단계가 독립적으로 검증되고 사용자 승인을 받은 뒤 다음 단계가 진행된다.
