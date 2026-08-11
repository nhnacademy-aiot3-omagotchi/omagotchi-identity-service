# Identity Service

계정, 인증과 전역 권한을 소유하는 서비스입니다. 현재 기본 구현 범위는 회원가입, Access JWT·Refresh Token 로그인, 인증 갱신, 로그아웃과 본인 조회입니다.

## 빠른 검증

Java 21과 Docker가 실행 중인 상태에서 다음 명령을 사용합니다. 테스트용 PostgreSQL 18.1과 RSA key pair는 테스트가 임시로 준비하므로 로컬 DB와 key 파일이 필요하지 않습니다.

```bash
./mvnw verify
```

## 일반 애플리케이션 실행

`local` profile은 저장소 루트의 `.env.local`을 읽습니다.

```bash
cp .env.local.example .env.local
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt-private.pem
openssl pkey -in secrets/jwt-private.pem -pubout -out secrets/jwt-public.pem
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

`.env.local`의 DB 접속값은 실행 환경에 맞게 설정합니다. `secrets/`, `.env.local`, DB 비밀번호와 private key는 Git에 커밋하지 않습니다.
`FRONTEND_USERNAME`과 `FRONTEND_PASSWORD`는 Frontend와 Identity에 같은 값을
주입합니다. 비밀번호는 URL-safe ASCII 영문자·숫자·`-`·`_`만 사용하는 32~72자
난수여야 합니다. 이 Credential은 로그인 전 Token 수명주기
API를 호출하는 Frontend 프로세스를 인증하며, 사용자를 인증하거나 외부 Client를 대비하는
설정이 아닙니다. 공유·운영 환경에서는
`openssl rand -hex 32`로 64자 난수를 생성합니다.

2026-07-21 기준 학교 PostgreSQL의 배정 데이터베이스는 `aiot3-team5-project`, 접속 사용자는 `aiot3-team5`로 확인했습니다. 해당 사용자는 데이터베이스 `CREATE` 권한이 있고 `identity_service` schema는 아직 없습니다. 최초 실행 시 Flyway가 schema와 `flyway_schema_history`를 생성하고 V1 계정, V2 Refresh Token Migration을 순서대로 적용합니다. 실제 적용 여부는 최초 실행 후 `flyway_schema_history`로 확인합니다.

## 패키지 읽는 법

각 기능은 다음 네 패키지를 기본으로 사용합니다.

- `domain`: Entity와 상태 규칙
- `application`: 기능 실행 순서, 내부 정책과 외부 기술에 요구하는 Port
- `infrastructure`: JPA, BCrypt와 JWT 같은 Port 구현
- `presentation`: HTTP 요청과 응답

Security 설정과 인증·인가 오류 처리는 `global.security`에, 공통 예외 응답은 `global.exception`에 둡니다. Application은 외부 기술 경계를 `application.port`로 제한하고, JPA·BCrypt·JWT 구현은 `infrastructure`가 소유합니다. 외부 I/O가 없는 SHA-256 Hash와 난수 Token 발급은 구체 Application Class로 둡니다. 모든 Use Case의 Interface나 `adapter`, `ServiceImpl` 계층은 만들지 않습니다.

구조 기준은 [Backend Code Structure](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/10-backend-code-structure.md), 오류 분류와 응답 기준은 [공통 예외 처리](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/04-error-handling.md)를 따릅니다.

Account 영속화는 `AccountRepository` Port를 `AccountJpaPersistence`가 구현하고, Spring Data Interface인 `AccountJpaRepository`에 위임합니다. 이메일 UNIQUE 위반은 `AccountJpaPersistence`가 `DUPLICATE_EMAIL`의 `BusinessException`으로 변환하고 원본 예외를 `cause`로 보존합니다.

`auth`는 `account.domain`을 직접 사용하지 않습니다. 계정 인증과 Refresh 허용 여부는 `AccountAuthenticationService`가 판단하고, `AccountAuthenticationResult`와 `AccountRefreshAccess`를 공개 Application 계약으로 사용합니다.

Frontend BFF의 이메일 로그인은 아래 순서로 읽으면 됩니다.

1. `LoginRequest`
2. `AuthController.login()` (`/api/v1/auth/login`)
3. `AuthenticationService.login()`
4. `AccountAuthenticationService.authenticate()`
5. `PasswordHasher` → `BcryptPasswordHasher`
6. `AccessTokenIssuer` → `JwtAccessTokenIssuer`
7. `RefreshTokenIssuer`
8. `TokenResponse`

`AccessTokenIssuer`의 `JwtAccessTokenIssuer` 구현은 RSA private key로 Access JWT를 발급합니다. 반대 방향의 Bearer Token 검증은 직접 만든 Filter가 아니라 Spring Security Resource Server가 public key로 처리합니다.

Refresh Token 갱신은 아래 순서로 읽으면 됩니다.

1. `AuthController.refresh()`
2. `RefreshTokenRequest`
3. `AuthenticationService.refresh()`
4. `RefreshTokenRotation.rotate()`
5. `RefreshTokenRepository.lockByHash()`
6. `RefreshToken.markUsed()`
7. `RefreshTokenIssuer.issue()`

Frontend BFF가 전달한 잘못된 로그인·Refresh Token은 공통 `BusinessException` 응답으로 처리합니다. 반면 RSA key, 필수 인증 설정과 암호화 알고리즘처럼 정상 실행 자체가 불가능한 오류는 애플리케이션 시작을 실패시킵니다.

예외는 다음 기준으로 구분합니다.

- 예상 가능한 사용자 요청 실패: Domain 검증 실패를 Application이 `BusinessException`으로 변환해 정해진 4xx 응답
- 하나의 외부 오류와 명확하게 대응하는 Persistence 실패: Infrastructure가 원본 `cause`를 보존한 `BusinessException`으로 직접 변환
- 요청 처리 중 불변식 위반: `IllegalArgumentException`·`IllegalStateException`을 공통 Handler가 500 응답으로 바꾸고 stack trace를 서버 로그에 기록
- 필수 설정·Bean 생성 실패: 시작 중 `IllegalStateException`이 전파되어 애플리케이션 시작 중단

## JWT 최소 개념

로그인에 성공하면 Identity Service가 private key로 서명한 Access JWT를 발급합니다. 클라이언트는 보호 API 요청의 `Authorization: Bearer <token>` 헤더에 이 Token을 보냅니다.

- `sub`: 계정 수명 동안 변경되지 않는 UUID `userId` (`accounts.id`)
- `role`: 전역 권한 `USER` 또는 `SYSTEM_ADMIN`
- `iss`: Token 발급자
- `aud`: Token을 사용할 대상 시스템
- `exp`: Token 만료 시각

다른 서비스는 public key와 같은 `iss`, `aud` 규칙으로 Access JWT를 직접 검증하므로 매 요청마다 Identity Service를 호출하지 않습니다. Frontend BFF는 Session에 보관한 Access JWT를 Gateway 요청에 전달하고, Gateway와 인증 경계가 적용된 도메인 서비스가 각각 Token을 검증합니다.

## 현재 인증 범위

- `POST /api/v1/auth/signup`: Frontend 전용 회원가입
- `POST /api/v1/auth/login`: Frontend 전용 로그인
- `POST /api/v1/auth/refresh`: Frontend 전용 Token 회전
- `POST /api/v1/auth/logout`: Frontend 전용 Token Family 폐기
- `GET /api/v1/users/me`: Access JWT 필요

인증 API는 HTTP Basic으로 Frontend 프로세스의 Credential을 확인하고 Access·Refresh
Token 원문과 만료 시각을 JSON으로 반환합니다. `/api/v1/auth/**`는 Gateway Route에
등록하지 않으며 Frontend가 Identity를 직접 호출합니다. Browser에는 Frontend Session
Cookie만 전달합니다.

Refresh Token은 예측하기 어려운 불투명 난수로 발급하고 DB에는 SHA-256 해시만 저장합니다. 최초 로그인 시점부터 기본 7일 동안 유효하며, 갱신할 때 Token을 회전해도 만료 시각은 연장하지 않습니다. 사용된 Token이 다시 들어오면 같은 로그인 단위의 Token family를 모두 폐기합니다. 로그아웃도 해당 family를 폐기하지만 이미 발급된 Access JWT는 `exp`까지 유효합니다.

Request ID의 공통 형식과 전파 규칙은 [Omagotchi HTTP Request ID 가이드](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/08-http-request-id.md)를 따릅니다. Identity Service에는 아직 적용하지 않았으며 Gateway와 각 서비스가 같은 값을 전달하도록 공통 관측성 작업에서 추가합니다.

## 서비스 내부 결정 기록

- [Refresh Token 회전 동시성 정책](docs/adr/0001-refresh-token-rotation-concurrency.md): 현재 Token 행 잠금과 family 폐기 동작, 동시 갱신의 결과와 고정 로그인 세션 행 도입 여부
