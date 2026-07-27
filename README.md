# Identity Service

계정, 인증과 전역 권한을 소유하는 서비스입니다. 현재 기본 구현 범위는 회원가입, Access JWT·Refresh Token 로그인, 인증 갱신, 로그아웃과 본인 조회입니다.

## 빠른 검증

Java 21과 Docker가 실행 중인 상태에서 다음 명령을 사용합니다. 테스트용 PostgreSQL 18.1과 RSA key pair는 테스트가 임시로 준비하므로 로컬 DB와 key 파일이 필요하지 않습니다.

```bash
./mvnw verify
```

## 일반 애플리케이션 실행

`local` profile은 저장소 루트의 `.env`를 읽습니다.

```bash
cp .env.example .env
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt-private.pem
openssl pkey -in secrets/jwt-private.pem -pubout -out secrets/jwt-public.pem
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

`.env`의 DB 접속값은 실행 환경에 맞게 설정합니다. `secrets/`, `.env`, DB 비밀번호와 private key는 Git에 커밋하지 않습니다.

2026-07-21 기준 학교 PostgreSQL의 배정 데이터베이스는 `aiot3-team5-project`, 접속 사용자는 `aiot3-team5`로 확인했습니다. 해당 사용자는 데이터베이스 `CREATE` 권한이 있고 `identity_service` schema는 아직 없습니다. 최초 실행 시 Flyway가 schema와 `flyway_schema_history`를 생성하고 V1 계정, V2 Refresh Token Migration을 순서대로 적용합니다. 실제 적용 여부는 최초 실행 후 `flyway_schema_history`로 확인합니다.

## 패키지 읽는 법

각 기능은 다음 네 패키지를 기본으로 사용합니다.

- `domain`: Entity와 상태 규칙
- `application`: 기능 실행 순서
- `infrastructure`: JPA Repository와 JWT 같은 기술 구현
- `presentation`: HTTP 요청과 응답

Security 설정과 인증·인가 오류 처리는 `global.security`에, 공통 예외 응답은 `global.exception`에 둡니다. 구현체가 하나뿐인 port, adapter와 `ServiceImpl` 계층은 만들지 않습니다.

JWT 이메일 로그인은 아래 순서로 읽으면 됩니다.

1. `LoginRequest`
2. `AuthController.login()`
3. `LoginUseCase.execute()`
4. `CredentialVerifier.matches()`
5. `AccessTokenIssuer.issue()`
6. `TokenResponse`
7. `SecurityConfig`
8. `AccountController.me()`

`AccessTokenIssuer`는 RSA private key로 Access JWT를 발급합니다. 반대 방향의 Bearer Token 검증은 직접 만든 Filter가 아니라 Spring Security Resource Server가 public key로 처리합니다.

Refresh Token 갱신은 아래 순서로 읽으면 됩니다.

1. `AuthController.refresh()`
2. `RefreshRequestOriginValidator.validate()`
3. `RefreshTokenUseCase.execute()`
4. `RefreshTokenRotation.rotate()`
5. `RefreshTokenStore.lockByHash()`
6. `RefreshToken.markUsed()`
7. `RefreshTokenIssuer.issue()`
8. `RefreshTokenCookieManager.issue()`

클라이언트의 잘못된 로그인·Refresh Token·Origin은 공통 `BusinessException` 응답으로 처리합니다. 반면 RSA key, 필수 인증 설정과 암호화 알고리즘처럼 정상 실행 자체가 불가능한 오류는 애플리케이션 시작을 실패시킵니다.

예외는 다음 기준으로 구분합니다.

- 예상 가능한 사용자 요청 실패: `BusinessException`으로 정해진 4xx 응답
- 요청 처리 중 불변식 위반: `IllegalArgumentException`·`IllegalStateException`으로 500 응답과 오류 로그, 애플리케이션은 계속 실행
- 필수 설정·Bean 생성 실패: 애플리케이션 시작 중단

## JWT 최소 개념

로그인에 성공하면 Identity Service가 private key로 서명한 Access JWT를 발급합니다. 클라이언트는 보호 API 요청의 `Authorization: Bearer <token>` 헤더에 이 Token을 보냅니다.

- `sub`: 계정 수명 동안 변경되지 않는 UUID `userId` (`accounts.id`)
- `role`: 전역 권한 `USER` 또는 `SYSTEM_ADMIN`
- `iss`: Token 발급자
- `aud`: Token을 사용할 대상 시스템
- `exp`: Token 만료 시각

다른 서비스는 public key와 같은 `iss`, `aud` 규칙으로 Access JWT를 직접 검증할 수 있으므로 매 요청마다 Identity Service를 호출하지 않습니다. 현재는 Identity Service 내부 발급·검증까지만 구현했고, Gateway와 각 도메인 서비스의 검증 설정과 public key 전달 방식은 아직 적용하지 않았습니다.

## 현재 인증 범위

- `POST /api/v1/auth/signup`: 공개
- `POST /api/v1/auth/login`: 공개
- `POST /api/v1/auth/refresh`: Refresh Cookie 필요
- `POST /api/v1/auth/logout`: Refresh Cookie가 없어도 멱등하게 `204`
- `GET /api/v1/users/me`: Access JWT 필요

Refresh Token은 예측하기 어려운 불투명 난수로 발급하고 DB에는 SHA-256 해시만 저장합니다. 최초 로그인 시점부터 기본 7일 동안 유효하며, 갱신할 때 Token을 회전해도 만료 시각은 연장하지 않습니다. 사용된 Token이 다시 들어오면 같은 로그인 단위의 Token family를 모두 폐기합니다. 로그아웃도 해당 family를 폐기하지만 이미 발급된 Access JWT는 `exp`까지 유효합니다.

Refresh Cookie는 `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`를 사용합니다. 운영 HTTPS에서는 `Secure=true`, 로컬 HTTP에서는 profile 설정으로 `false`를 사용합니다. Refresh와 로그아웃은 `AUTH_ALLOWED_ORIGINS`에 등록한 Origin만 허용합니다.

Request ID의 공통 형식과 전파 규칙은 [Omagotchi HTTP Request ID 가이드](../docs/50-guides/08-http-request-id.md)를 따릅니다. Identity Service에는 아직 적용하지 않았으며 Gateway와 각 서비스가 같은 값을 전달하도록 공통 관측성 작업에서 추가합니다.

## 서비스 내부 결정 기록

- [Refresh Token 회전 동시성 정책](docs/adr/0001-refresh-token-rotation-concurrency.md): 현재 Token 행 잠금과 family 폐기 동작, 동시 갱신의 결과와 고정 로그인 세션 행 도입 여부
