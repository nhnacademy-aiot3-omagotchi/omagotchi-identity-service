package site.omagotchi.identityservice.account.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.identityservice.account.domain.Account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository
        extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT account
            FROM Account account
            WHERE account.id = :accountId
            """)
    Optional<Account> lockById(@Param("accountId") UUID accountId);

    // 교차 관리자 요청의 잠금 순서 고정
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT account
            FROM Account account
            WHERE account.id IN :accountIds
            ORDER BY account.id ASC
            """)
    List<Account> lockAllByIdInOrder(
            @Param("accountIds") Collection<UUID> accountIds
    );

    // 관리자 감소 요청만 직렬화하는 단일 행 잠금
    @Query(value = """
            SELECT id
            FROM identity_service.system_administrator_guards
            WHERE id = 1
            FOR UPDATE
            """, nativeQuery = true)
    Integer lockSystemAdministratorGuard();

    // ACTIVE 또는 LOCKED 상태의 SYSTEM_ADMIN 집계
    @Query("""
            SELECT COUNT(account)
            FROM Account account
            WHERE account.globalRole = site.omagotchi.identityservice.account.domain.GlobalRole.SYSTEM_ADMIN
              AND account.status IN (
                  site.omagotchi.identityservice.account.domain.AccountStatus.ACTIVE,
                  site.omagotchi.identityservice.account.domain.AccountStatus.LOCKED
              )
            """)
    long countUsableSystemAdministrators();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT account
            FROM Account account
            WHERE account.email = :email
            """)
    Optional<Account> lockByEmail(@Param("email") String email);

    @Query("""
            SELECT account
             FROM Account account
             WHERE account.id IN :candidateIds
               AND account.status = site.omagotchi.identityservice.account.domain.AccountStatus.ACTIVE
               AND (LOWER(account.name) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!'
                OR account.email LIKE CONCAT('%', LOWER(:query), '%') ESCAPE '!')
             ORDER BY account.name ASC, account.email ASC, account.id ASC
            """)
    List<Account> searchByNameOrEmail(
            @Param("query") String query,
            @Param("candidateIds") Collection<UUID> candidateIds,
            Pageable pageable
    );
}
