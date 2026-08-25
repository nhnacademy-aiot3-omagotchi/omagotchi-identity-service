# Identity Service Architecture Decision Records

Identity Service 하나에서 결정하고 구현할 수 있는 인증·계정 설계는 이 폴더에 기록합니다. 여러 서비스가 함께 따라야 하는 JWT claim, 인증 경계와 사용자 식별자 계약은 중앙 `docs/30-adr`에서 관리합니다.

| ADR | 상태 |
|---|---|
| [0001 Refresh Token 회전 동시성 정책](0001-refresh-token-rotation-concurrency.md) | Accepted |
| [0002 계정 인증·Refresh Session 직렬화](0002-account-authentication-refresh-session-serialization.md) | Accepted |

상태는 `Proposed`, `Accepted`, `Superseded`, `Rejected` 중 하나를 사용합니다. 구현과 검증이 끝나지 않은 대안을 억지로 `Accepted`로 만들지 않습니다.
