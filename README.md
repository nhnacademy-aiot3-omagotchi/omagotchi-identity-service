# Identity Service

계정·인증·전역 권한 소유 서비스.

## 담당 범위

- 계정: 회원가입, 본인 정보 조회·수정·탈퇴
- 인증: 이메일·비밀번호 검증, Access JWT 발급
- 세션: Refresh Token 회전, 재사용 탐지, 로그아웃
- 전역 권한: `USER`, `SYSTEM_ADMIN`
- Token 계약: RSA 서명, `iss`·`aud`·`exp` 검증
- Frontend 경계: 인증 API의 Frontend 프로세스 Credential 검증
- Learning 경계: 계정 상태·표시 이름 조회의 Learning 프로세스 Credential 검증

## 기술 구성

- Java 21, Spring Boot 4.1
- Spring Security, OAuth2 Resource Server
- Spring Data JPA, PostgreSQL 18.1
- Flyway, Eureka Client
- Testcontainers PostgreSQL 18.1

## 빠른 검증

- 선행 조건: Docker 호환 Container Runtime 실행
- 테스트 DB·RSA Key: 테스트에서 임시 생성
- 로컬 DB·Key 파일: 불필요

```bash
./mvnw verify
```

- 생성 API 문서: `target/generated-docs/index.html`

## 로컬 실행

### 환경 준비

```bash
cp .env.local.example .env.local

mkdir -p secrets
chmod 700 secrets

openssl genpkey \
  -algorithm RSA \
  -pkeyopt rsa_keygen_bits:2048 \
  -out secrets/jwt-private.pem

openssl pkey \
  -in secrets/jwt-private.pem \
  -pubout \
  -out secrets/jwt-public.pem

chmod 600 secrets/jwt-private.pem
chmod 644 secrets/jwt-public.pem
```

- Profile: `local`
- 설정 파일: 저장소 루트 `.env.local`
- 기본 Port: `8083`
- 기본 DB: `jdbc:postgresql://localhost:5432/identity_service`
- Eureka: 기본 비활성화
- Git 추적 제외: `.env.local`, `secrets/`
- Private Key: Identity의 JWT 발급 전용
- Public Key: Gateway·Identity·Domain Service의 JWT 검증용

### Frontend Credential

- 용도: Frontend 프로세스의 `/api/v1/auth/**` 호출 인증
- 설정: Frontend와 Identity에 동일 값 주입
- Browser 사용자 Credential과의 분리
- 형식: URL-safe 문자 32~72자
- 공유 환경 생성 예시: `openssl rand -hex 32`

### Learning Credential

- 용도: Learning 프로세스의 `/api/v1/internal/accounts/**` 호출 인증
- 설정: Learning과 Identity에 동일 값 주입
- Frontend Credential·Rule Engine 공유 Credential과의 분리
- 형식: URL-safe 문자 32~72자
- 공유 환경 생성 예시: `openssl rand -hex 32`

### 로그인 보호 설정

- `LOGIN_MAXIMUM_FAILED_ATTEMPTS`: 계정 잠금까지 허용할 연속 로그인 실패 횟수
- `LOGIN_LOCK_DURATION`: 잠금 유지 기간의 ISO-8601 Duration
- 로컬·개발 예제 정책: `5`, `PT10M`
- 두 값 모두 필수이며 누락·범위 오류 시 애플리케이션 시작 실패

### 실행

```bash
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

- Health: <http://localhost:8083/actuator/health>

## 환경 Profile

- `local`: `.env.local`, 로컬 DB·RSA Key, Eureka 기본 비활성화
- `dev`: `.env.dev`, 공유 개발 DB, Flyway 기본 비활성화
- `test`: Testcontainers DB·테스트 Key, 외부 Eureka 미사용
- `prod`: 운영 환경변수·Mount된 RSA Key, Eureka 활성화

## HTTP API

| Method | Path | 인증 | 용도 |
|---|---|---|---|
| `POST` | `/api/v1/auth/signup` | Frontend Credential | 회원가입 |
| `POST` | `/api/v1/auth/login` | Frontend Credential | 로그인·Token 발급 |
| `POST` | `/api/v1/auth/refresh` | Frontend Credential | Refresh Token 회전 |
| `POST` | `/api/v1/auth/logout` | Frontend Credential | Token Family 폐기 |
| `GET` | `/api/v1/users/me` | Access JWT | 본인 정보 조회 |
| `PATCH` | `/api/v1/users/me` | Access JWT | 본인 이름 변경 |
| `PATCH` | `/api/v1/users/me/password` | Access JWT | 현재 비밀번호 확인 후 비밀번호 변경·전체 Refresh Session 폐기 |
| `DELETE` | `/api/v1/users/me` | Access JWT | 현재 비밀번호 확인 후 본인 탈퇴·전체 Refresh Session 폐기 |
| `GET` | `/api/v1/admin/users` | Access JWT (`SYSTEM_ADMIN`) | 사용자 목록 페이지 조회·검색 |
| `PATCH` | `/api/v1/admin/accounts/{user-id}/status` | SYSTEM_ADMIN Access JWT | 계정 활성화·비활성화와 영속 감사 기록 |
| `GET` | `/api/v1/internal/accounts/{accountId}` | Learning Credential | 계정 상태·표시 이름 단건 조회 |
| `POST` | `/api/v1/internal/accounts/batch` | Learning Credential | 계정 상태·표시 이름 일괄 조회 |
| `POST` | `/api/v1/internal/accounts/search` | Learning Credential | Learning 후보 ID 범위 내 이름·이메일 검색(최대 20건) |

- 관리자 목록: 기본 20건, 최대 100건, 기본 정렬 최신 가입순, 정렬 기준은 화이트리스트 고정
- 관리자 목록 인가: Filter Chain의 `role` Claim 검사 이후 요청 시점 DB 권한·상태 재검증
- 일괄 조회: 특정 계정 ID 묶음의 단순 목록 응답, 요청당 최대 100개
- 페이지 응답 제외: 전체 계정 목록 검색이 아닌 요청 ID 집합 조회

### 호출 경계

- `/api/v1/auth/**`: Gateway Route 미등록
- `/api/v1/internal/**`: Gateway Route 미등록
- `/api/v1/admin/users`: Gateway Route 등록
- 인증 API: Frontend → Identity 직접 호출
- 계정 조회 API: Learning → Identity 직접 호출
- Browser 보관값: Frontend Session Cookie
- Browser 미노출값: Access JWT, Refresh Token
- 외부 보호 API: Gateway의 1차 JWT 검증
- Domain Service: 전달받은 Access JWT 재검증

## Token 정책

### Access JWT

- 서명: Identity Private Key
- 검증: 각 보호 서비스의 Public Key
- 주요 Claim: `sub`, `role`, `iss`, `aud`, `exp`
- 기본 수명: `PT15M`
- 매 요청 Identity 조회: 불필요

### Refresh Token

- 형식: 불투명 난수
- 저장: SHA-256 Hash
- 기본 Family 수명: 최초 로그인부터 `P7D`
- 갱신: Token 회전, Family 만료 시각 연장 없음
- 재사용 탐지·로그아웃: 동일 Family 일괄 폐기
- Access JWT: 별도 폐기 목록 없이 `exp`까지 유효
- 동시성 경계: 계정 행을 먼저 잠근 뒤 요청 Token 행 잠금
- 사용자 전체 폐기: 계정 행 잠금 뒤 모든 미폐기 Token 일괄 폐기
- 로그인 실패 잠금: 새 비밀번호 로그인만 차단하며 기존 Refresh Token은 유지

## Database·코드 구조

- Schema: `identity_service`
- Migration: `src/main/resources/db/migration/`
- JPA 정책: `ddl-auto=validate`
- `account`: 계정 생성·조회·인증 근거
- `accountstate`: 본인 탈퇴·관리자 계정 상태 변경 조정과 감사 기록
- `auth`: Access JWT·Refresh Token 수명주기
- `global.config`: Service 공통 시간·Password Encoder 등 Framework 설정
- `global.security.basic`: 서비스 Credential 인증 Provider 조립
- `global.security.frontend`: Frontend 전용 HTTP Basic 경계
- `global.security.learning`: Learning 전용 HTTP Basic 경계
- `global.security.jwt`: Access JWT 발급·검증과 기본 보호 API 경계
- `global.security.error`: 인증·인가 공통 오류 응답
- `global.exception`: 공통 오류 응답
- 내부 계층: `domain` → `application` → `infrastructure`·`presentation`

## 운영 원칙

- 적용 완료 Migration의 변경 금지
- Password·Token·Cookie·Private Key의 기록 금지
- 필수 설정·RSA Key 오류의 애플리케이션 시작 실패
- Request ID 공통 적용 전 상태
- 본인 탈퇴와 계정 비활성화는 Refresh Session만 즉시 폐기하며, 기존 Access JWT는 최대 15분간 유효할 수 있음
- `SYSTEM_ADMIN`의 자기 비활성화와 마지막 이용 가능 관리자(`ACTIVE`·`LOCKED`)의 소실 방지
- 마지막 관리자 보호는 감소 작업만 단일 보호 행으로 직렬화하며, 계정 조회나 일반 사용자 상태 변경은 해당 행을 잠그지 않음
- 최초 `SYSTEM_ADMIN` 승격은 관리자 수를 늘리는 통제된 DB 작업으로 수행
- 직접 SQL 역할 강등·`DISABLED`·`WITHDRAWN` 전환은 애플리케이션 보호를 우회하므로 서비스 쓰기 트래픽과 동시에 수행하지 않음
- 운영 SQL에서 관리자 감소가 불가피하면 동일 트랜잭션에서 대상 Account 행을 UUID 순으로 잠근 뒤 보호 행을 잠금
- 보호 행 획득 후 `ACTIVE`·`LOCKED` SYSTEM_ADMIN 수를 다시 계산하고, 예정 변경 적용 뒤 한 명도 남지 않으면 전체 Rollback
- 운영 SQL에서도 보호 행보다 Account 행을 먼저 잠그며, 역순 잠금 금지

## 관련 문서

- [Backend Code Structure](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/10-backend-code-structure.md)
- [공통 예외 처리](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/04-error-handling.md)
- [HTTP Request ID](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/08-http-request-id.md)
- [Refresh Token 회전 동시성 정책](docs/adr/0001-refresh-token-rotation-concurrency.md)
- [계정 인증·Refresh Session 직렬화](docs/adr/0002-account-authentication-refresh-session-serialization.md)
