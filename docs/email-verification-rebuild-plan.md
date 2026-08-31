# 이메일 인증 재작성 구현 계획

이 문서는 [ADR 0003](adr/0003-postgresql-email-verification.md)의 구현 추적표입니다. 구현 중 새로운
판단이 필요하면 코드를 먼저 바꾸지 않고 ADR 또는 이 문서를 갱신합니다.

## 변경 불변식

1. PostgreSQL 업무 Transaction만 원자성을 보장한다고 가정한다.
2. 외부 메일 전송을 Rollback하거나 DB 행 삭제로 없었던 일처럼 만들지 않는다.
3. 쿨다운·현재 Challenge 선택·실패 횟수는 PostgreSQL 잠금으로 직렬화한다.
4. 인증 성공과 회원가입·비밀번호 변경은 같은 Transaction에서 Commit한다.
5. v1 API의 경로와 동작은 유지한다.
6. 인증번호 원문과 비밀 설정값은 저장·로그·응답하지 않는다.

## 구현 단계

| 단계 | 변경 목적 | 완료 조건 | 상태 |
|---|---|---|---|
| 1 | 결정과 API 계약 문서화 | ADR·구현 추적표 작성 | 완료 |
| 2 | PostgreSQL 인증 모델 | Migration, 상태 전이, Scope 잠금 저장소 | 완료 |
| 3 | 동기 메일 발급 | HMAC·난수 생성, Resend Port/Adapter, 발급 API | 완료 |
| 4 | 업무 Transaction 결합 | v2 회원가입·비밀번호 변경과 Challenge 소비 | 완료 |
| 5 | 외부 오류 계약 | 429/`Retry-After`, 503, 보안 경로 설정 | 완료 |
| 6 | 검증과 운영 문서 | 단위·통합 테스트, README·환경변수·API 문서 | 부분 완료 |

## 파일별 변경 기록

구현 완료 시 각 변경의 이유와 검증 근거를 아래에 기록합니다.

| 영역 | 변경 | 이유 | 검증 |
|---|---|---|---|
| 문서 | ADR 0003과 이 계획 추가 | 구현이 즉흥적으로 분기되지 않도록 결정·범위를 고정 | 문서 자체 검토 |
| DB | V3 Scope·Challenge Migration 추가 | 쿨다운과 OTP 상태를 PostgreSQL 잠금으로 직렬화 | Flyway·JPA 통합 테스트 작성 |
| Domain | 인증 상태와 전달 상태 분리 | 메일 장애와 OTP 사용 가능 여부를 독립적으로 설명 | 상태 전이 단위 테스트 |
| 보안 | 숫자 6자리 생성·HMAC-SHA256 저장·상수 시간 비교 | DB 유출과 Timing 비교 위험 완화 | HMAC 문맥·코드 불일치 테스트 |
| 발급 | DB Commit 뒤 제한 시간 내 Resend 동기 호출 | DB Rollback 불가능한 외부 호출을 명시적 경계로 분리하고 만료된 202 방지 | 성공·실패·지연·상태 기록 실패 단위 테스트 |
| 보상 | 최신 Challenge의 전달 실패만 쿨다운 해제 | 오래된 실패가 새 쿨다운을 삭제하지 않게 함 | Scope 소유권 단위 테스트·API 통합 테스트 작성 |
| 업무 | v2 가입·비밀번호 변경과 Challenge 소비 결합 | 업무 실패 시 소비도 같은 DB Transaction에서 Rollback | Transaction 단위 테스트 |
| 오류 | 429/`Retry-After`, 503 공개 계약 추가 | 재시도 가능 시점과 외부 의존성 장애를 안정적으로 표현 | 공통 예외 Handler 테스트 |
| API·보안 | v2 네 API와 Frontend/Bearer 경계 추가 | 기존 v1을 유지하며 이메일 인증 흐름을 도입 | Controller 단위·Security 통합 테스트 작성 |
| 설정 | OTP 정책·HMAC·Resend·호출 제한 필수 환경변수 추가 | 환경별 정책과 `connect ≤ read < TTL`을 코드 기본값 없이 명시 | 설정 바인딩·시간 관계·지연 응답 테스트 |
| 운영 문서 | README·Asciidoc API·환경 예제 갱신 | 실행자와 호출자가 새 계약을 재현 | 링크·Diff 검토 |
| 테스트 구조 | 전체 테스트 메서드에 Given/When/Then 단계 표기 | 준비·실행·검증의 책임과 실패 지점을 빠르게 구분 | 192개 테스트 메서드 정적 검사·전체 단위 테스트 |
| 테스트 데이터 | 임의 UUID를 의미가 드러나는 정적 UUID로 교체 | 실패 재현성과 실행 간 로그 비교를 안정화 | `src/test`의 `randomUUID()` 0건 정적 검사·전체 단위 테스트 |

## 검증 기록

- `mvn test`: 140개 단위·MVC 테스트 성공
- Resend 지연 테스트: 2초 지연 응답을 100ms read timeout으로 중단하고 `EmailDeliveryException` 전환 확인
- 만료 경계 테스트: 메일 처리 완료 시 Challenge가 만료되었으면 쿨다운 해제 후 `503` 반환 확인
- Given/When/Then 정적 검사: 192개 테스트 메서드 모두 세 단계 주석 포함
- 결정적 UUID 정적 검사: `src/test`의 `randomUUID()` 호출 0건
- `mvn verify -DskipITs`: 140개 테스트와 JaCoCo 60% 기준 성공
- 통합 테스트: 로컬 Docker 호환 Runtime을 찾지 못해 74개가 Context 기동 전에 중단
- 통합 테스트 실패 원인: `Previous attempts to find a Docker environment failed`; 코드·Migration 실패는 관측되지 않음
- `EmailVerificationApiIT`에 실제 PostgreSQL 기준 발급·쿨다운·실패 횟수 Commit·일회 소비·메일 실패 보상 시나리오 작성

Docker Runtime에서 `mvn clean verify`가 성공하기 전까지 ADR 0003의 상태는 `Proposed`로 유지합니다.

## 중단 조건

- 메일 SDK가 idempotency key를 지원하지 않음
- 현재 계정·Refresh Session 잠금 순서와 Challenge 잠금이 교착을 만들 수 있음
- 인증 실패 횟수를 Commit하면서 업무 변경만 Rollback할 수 없는 구조가 발견됨

중단 조건이 발생하면 우회 코드를 추가하지 않고 Transaction 순서와 API 계약을 다시 결정합니다.
