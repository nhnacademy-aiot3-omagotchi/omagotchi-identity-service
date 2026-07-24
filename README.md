# Identity Service

계정, 인증과 전역 권한을 소유하는 서비스입니다. 현재 기본 구현 범위는 회원가입, Access JWT 로그인과 본인 조회입니다. Refresh Token과 서버 로그아웃은 별도 단계에서 추가합니다.

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

2026-07-21 기준 학교 PostgreSQL의 배정 데이터베이스는 `aiot3-team5-project`, 접속 사용자는 `aiot3-team5`로 확인했습니다. 해당 사용자는 데이터베이스 `CREATE` 권한이 있고 `identity_service` schema는 아직 없습니다. 최초 실행 시 Flyway가 schema와 `flyway_schema_history`를 생성하고 V1 Migration을 적용하도록 설정했으며, 실제 적용 여부는 최초 실행 후 `flyway_schema_history`로 확인합니다.

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
- `GET /api/v1/users/me`: Access JWT 필요

현재는 Access JWT 발급·검증까지만 구현했습니다. Refresh Token, `/refresh`, 서버 측 `/logout`은 구현하지 않았고 프론트엔드의 Token 보관 방식도 아직 확정하지 않았습니다. 클라이언트가 Access JWT를 버려도 이미 발급된 Token은 `exp`까지 유효합니다.

Request ID 역시 아직 팀 공통 규칙을 결정하지 않았습니다. Identity Service에만 먼저 추가하지 않고 Gateway와 각 서비스가 같은 값을 전달하도록 별도 작업에서 정합니다.
