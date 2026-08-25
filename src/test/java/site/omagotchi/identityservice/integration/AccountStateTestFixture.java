package site.omagotchi.identityservice.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import site.omagotchi.identityservice.account.domain.AccountStatus;

import java.util.UUID;

// 아직 상태 변경 Use Case가 없는 Account의 영속 상태를 구성하는 통합 Test Fixture
@RequiredArgsConstructor
final class AccountStateTestFixture {

    private final JdbcTemplate jdbcTemplate;

    void changeStatus(UUID accountId, AccountStatus accountStatus) {
        // Production API·Domain 메서드의 Test 전용 추가를 피하기 위한 DB Fixture 경계
        int updatedRows = jdbcTemplate.update(
                """
                        UPDATE identity_service.accounts
                        SET status = ?,
                            locked_until = CASE
                                WHEN ? = 'LOCKED' THEN CURRENT_TIMESTAMP + INTERVAL '1 hour'
                                ELSE NULL
                            END,
                            withdrawn_at = CASE
                                WHEN ? = 'WITHDRAWN' THEN CURRENT_TIMESTAMP
                                ELSE NULL
                            END
                        WHERE id = ?
                        """,
                accountStatus.name(),
                accountStatus.name(),
                accountStatus.name(),
                accountId
        );

        if (updatedRows != 1) {
            throw new IllegalStateException("Account Test Fixture 대상이 존재하지 않습니다.");
        }
    }

    void expireLoginLock(UUID accountId) {
        int updatedRows = jdbcTemplate.update(
                """
                        UPDATE identity_service.accounts
                        SET locked_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                        WHERE id = ?
                          AND status = 'LOCKED'
                        """,
                accountId
        );

        if (updatedRows != 1) {
            throw new IllegalStateException("잠금 만료 Test Fixture 대상이 존재하지 않습니다.");
        }
    }
}
