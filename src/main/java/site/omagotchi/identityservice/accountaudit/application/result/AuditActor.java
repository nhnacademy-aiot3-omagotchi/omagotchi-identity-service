package site.omagotchi.identityservice.accountaudit.application.result;

import java.util.UUID;

/**
 * 감사 기록에 등장하는 사람.
 *
 * <p>이름은 조회 시점의 계정 이름이다. 감사 테이블은 이름을 복사해 두지 않으므로
 * 개명하면 과거 기록의 표시 이름도 함께 바뀐다. 누가 했는지는 UUID 가 보장한다.</p>
 */
public record AuditActor(UUID userId, String name) {

    // 계정이 조회되지 않는 경우에도 감사 한 줄을 통째로 잃지 않는다.
    public static AuditActor unknown(UUID userId) {
        return new AuditActor(userId, null);
    }
}
