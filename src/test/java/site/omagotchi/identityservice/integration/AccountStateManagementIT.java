package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.account.domain.GlobalRole;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeAction;
import site.omagotchi.identityservice.accountstate.domain.AccountStatusChangeAudit;
import site.omagotchi.identityservice.accountstate.infrastructure.AccountStatusChangeAuditJpaRepository;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class AccountStateManagementIT {

    private static final String PASSWORD = "password-passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private AccountStatusChangeAuditJpaRepository auditJpaRepository;

    private AuthApiTestClient api;
    private AccountStateTestFixture fixture;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        fixture = new AccountStateTestFixture(jdbcTemplate);
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("본인 탈퇴 시 상태·시각 기록과 모든 Refresh Session 폐기")
    void withdrawsSelfAndRevokesEveryRefreshSession() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("withdraw@example.com");
        AuthApiTestClient.TokenBundle firstLogin = api.loginSuccessfully(
                "withdraw@example.com",
                PASSWORD
        );
        AuthApiTestClient.TokenBundle secondLogin = api.loginSuccessfully(
                "withdraw@example.com",
                PASSWORD
        );

        // When
        api.withdraw(firstLogin.accessToken(), PASSWORD)
                .andExpectAll(
                        status().isNoContent(),
                        header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store"))
                );

        // Then
        Account withdrawn = accountJpaRepository.findById(accountId).orElseThrow();
        List<RefreshToken> tokens = tokensFor(accountId);
        thenSoftly(softly -> {
            softly.then(withdrawn.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
            softly.then(withdrawn.getWithdrawnAt()).isNotNull();
            softly.then(tokens).hasSize(2).allSatisfy(token -> {
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevocationReason())
                        .isEqualTo(RefreshTokenRevocationReason.ACCOUNT_WITHDRAWN);
            });
            softly.then(auditJpaRepository.count()).isZero();
        });
        api.me(firstLogin.accessToken())
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.status").value("WITHDRAWN")
                );
        api.login("withdraw@example.com", PASSWORD).andExpect(status().isUnauthorized());
        api.refresh(secondLogin.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("현재 비밀번호 불일치 시 본인 탈퇴와 Refresh 폐기 생략")
    void rejectsWithdrawalWithWrongPassword() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("wrong-password@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "wrong-password@example.com",
                PASSWORD
        );

        // When
        api.withdraw(login.accessToken(), "wrong-password-passphrase")
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("ACCOUNT_CURRENT_PASSWORD_MISMATCH")
                );

        // Then
        Account account = accountJpaRepository.findById(accountId).orElseThrow();
        thenSoftly(softly -> {
            softly.then(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(account.getWithdrawnAt()).isNull();
            softly.then(tokensFor(accountId)).hasSize(1).allSatisfy(token ->
                    softly.then(token.isRevoked()).isFalse()
            );
        });
    }

    @Test
    @DisplayName("LOCKED 계정도 본인 탈퇴와 Refresh 폐기 허용")
    void withdrawsLockedAccount() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("locked-withdrawal@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "locked-withdrawal@example.com",
                PASSWORD
        );
        fixture.changeStatus(accountId, AccountStatus.LOCKED);

        // When
        api.withdraw(login.accessToken(), PASSWORD).andExpect(status().isNoContent());

        // Then
        Account withdrawn = accountJpaRepository.findById(accountId).orElseThrow();
        thenSoftly(softly -> {
            softly.then(withdrawn.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
            softly.then(withdrawn.getLockedUntil()).isNull();
            softly.then(tokensFor(accountId)).hasSize(1).allSatisfy(token ->
                    softly.then(token.getRevocationReason())
                            .isEqualTo(RefreshTokenRevocationReason.ACCOUNT_WITHDRAWN)
            );
        });
    }

    @Test
    @DisplayName("DISABLED 계정은 비밀번호 값과 무관하게 본인 탈퇴 거부")
    void rejectsDisabledAccountWithdrawalBeforePasswordVerification() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("disabled-withdrawal@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "disabled-withdrawal@example.com",
                PASSWORD
        );
        fixture.changeStatus(accountId, AccountStatus.DISABLED);

        // When
        ResultActions response = api.withdraw(
                login.accessToken(),
                "wrong-password-passphrase"
        );

        // Then
        response
                .andExpectAll(
                        status().isConflict(),
                        jsonPath("$.code").value("ACCOUNT_WITHDRAWAL_NOT_ALLOWED")
                );
        thenSoftly(softly -> {
            softly.then(accountJpaRepository.findById(accountId).orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.DISABLED);
            softly.then(tokensFor(accountId)).hasSize(1).allSatisfy(token ->
                    softly.then(token.isRevoked()).isFalse()
            );
        });
    }

    @Test
    @DisplayName("이미 탈퇴한 계정의 재요청은 부수효과 없는 204")
    void treatsRepeatedWithdrawalAsNoOp() throws Exception {
        // Given
        UUID accountId = api.signupSuccessfully("repeat-withdrawal@example.com");
        AuthApiTestClient.TokenBundle login = api.loginSuccessfully(
                "repeat-withdrawal@example.com",
                PASSWORD
        );
        api.withdraw(login.accessToken(), PASSWORD).andExpect(status().isNoContent());
        Account firstState = accountJpaRepository.findById(accountId).orElseThrow();
        Instant firstWithdrawnAt = firstState.getWithdrawnAt();
        Instant firstRevokedAt = tokensFor(accountId).getFirst().getRevokedAt();

        // When
        api.withdraw(login.accessToken(), PASSWORD).andExpect(status().isNoContent());

        // Then
        Account repeatedState = accountJpaRepository.findById(accountId).orElseThrow();
        RefreshToken repeatedToken = tokensFor(accountId).getFirst();
        thenSoftly(softly -> {
            softly.then(repeatedState.getWithdrawnAt()).isEqualTo(firstWithdrawnAt);
            softly.then(repeatedToken.getRevokedAt()).isEqualTo(firstRevokedAt);
            softly.then(repeatedToken.getRevocationReason())
                    .isEqualTo(RefreshTokenRevocationReason.ACCOUNT_WITHDRAWN);
        });
    }

    @Test
    @DisplayName("SYSTEM_ADMIN 비활성화 시 Refresh 폐기와 영속 감사 기록")
    void disablesAccountAndRecordsAudit() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "admin@example.com"
        );
        UUID targetId = api.signupSuccessfully("target@example.com");
        AuthApiTestClient.TokenBundle targetLogin = api.loginSuccessfully(
                "target@example.com",
                PASSWORD
        );
        api.loginSuccessfully("target@example.com", PASSWORD);

        // When
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "DISABLED",
                        "  보안 사고 대응  "
                )
                .andExpectAll(
                        status().isNoContent(),
                        header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store"))
                );

        // Then
        Account target = accountJpaRepository.findById(targetId).orElseThrow();
        AccountStatusChangeAudit audit = auditJpaRepository.findAll().getFirst();
        thenSoftly(softly -> {
            softly.then(target.getStatus()).isEqualTo(AccountStatus.DISABLED);
            softly.then(tokensFor(targetId)).hasSize(2).allSatisfy(token -> {
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevocationReason())
                        .isEqualTo(RefreshTokenRevocationReason.ACCOUNT_DISABLED);
            });
            softly.then(audit.getActorUserId()).isEqualTo(administrator.userId());
            softly.then(audit.getTargetUserId()).isEqualTo(targetId);
            softly.then(audit.getAction())
                    .isEqualTo(AccountStatusChangeAction.ACCOUNT_DISABLED);
            softly.then(audit.getBeforeStatus().name()).isEqualTo("ACTIVE");
            softly.then(audit.getAfterStatus().name()).isEqualTo("DISABLED");
            softly.then(audit.getReason()).isEqualTo("보안 사고 대응");
            softly.then(audit.getRequestId()).isNull();
        });
        api.me(targetLogin.accessToken())
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.status").value("DISABLED")
                );
    }

    @Test
    @DisplayName("잠금 해제는 Refresh를 유지하고 ACCOUNT_UNLOCKED 감사 기록")
    void unlocksAccountWithoutRevokingRefreshSession() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "unlock-admin@example.com"
        );
        UUID targetId = api.signupSuccessfully("locked-target@example.com");
        api.loginSuccessfully("locked-target@example.com", PASSWORD);
        fixture.changeStatus(targetId, AccountStatus.LOCKED);

        // When
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "ACTIVE",
                        "본인 확인 완료"
                )
                .andExpect(status().isNoContent());

        // Then
        Account target = accountJpaRepository.findById(targetId).orElseThrow();
        AccountStatusChangeAudit audit = auditJpaRepository.findAll().getFirst();
        thenSoftly(softly -> {
            softly.then(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(target.getFailedLoginAttempts()).isZero();
            softly.then(target.getLockedUntil()).isNull();
            softly.then(tokensFor(targetId)).hasSize(1).allSatisfy(token ->
                    softly.then(token.isRevoked()).isFalse()
            );
            softly.then(audit.getAction())
                    .isEqualTo(AccountStatusChangeAction.ACCOUNT_UNLOCKED);
        });
    }

    @Test
    @DisplayName("재활성화 뒤에도 비활성화 전 Refresh Token은 계속 폐기 상태")
    void reactivatesWithoutRestoringRevokedRefreshSession() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "reactivate-admin@example.com"
        );
        UUID targetId = api.signupSuccessfully("disabled-target@example.com");
        AuthApiTestClient.TokenBundle targetLogin = api.loginSuccessfully(
                "disabled-target@example.com",
                PASSWORD
        );
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "DISABLED",
                        "보안 확인 필요"
                )
                .andExpect(status().isNoContent());

        // When
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "ACTIVE",
                        "보안 확인 완료"
                )
                .andExpect(status().isNoContent());

        // Then
        Account target = accountJpaRepository.findById(targetId).orElseThrow();
        List<AccountStatusChangeAudit> audits = auditJpaRepository.findAll();
        thenSoftly(softly -> {
            softly.then(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            softly.then(tokensFor(targetId)).hasSize(1).allSatisfy(token -> {
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevocationReason())
                        .isEqualTo(RefreshTokenRevocationReason.ACCOUNT_DISABLED);
            });
            softly.then(audits).extracting(AccountStatusChangeAudit::getAction)
                    .containsExactlyInAnyOrder(
                            AccountStatusChangeAction.ACCOUNT_DISABLED,
                            AccountStatusChangeAction.ACCOUNT_REACTIVATED
                    );
        });
        api.refresh(targetLogin.refreshToken()).andExpect(status().isUnauthorized());
        api.loginSuccessfully("disabled-target@example.com", PASSWORD);
    }

    @Test
    @DisplayName("DISABLED 동일 상태 요청은 감사와 Refresh 폐기를 중복하지 않는 204")
    void treatsRepeatedDisableAsNoOp() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "noop-admin@example.com"
        );
        UUID targetId = api.signupSuccessfully("noop-target@example.com");
        api.loginSuccessfully("noop-target@example.com", PASSWORD);
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "DISABLED",
                        "최초 비활성화"
                )
                .andExpect(status().isNoContent());
        RefreshToken firstToken = tokensFor(targetId).getFirst();
        Instant firstRevokedAt = firstToken.getRevokedAt();
        RefreshToken issuedAfterDisable = refreshTokenJpaRepository.saveAndFlush(
                RefreshToken.issue(
                        targetId,
                        UUID.randomUUID(),
                        "a".repeat(64),
                        Instant.now().plusSeconds(3600),
                        Instant.now()
                )
        );

        // When
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "DISABLED",
                        "비활성화 재요청"
                )
                .andExpect(status().isNoContent());

        // Then
        thenSoftly(softly -> {
            softly.then(accountJpaRepository.findById(targetId).orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.DISABLED);
            softly.then(refreshTokenJpaRepository.findById(firstToken.getId())
                            .orElseThrow().getRevokedAt())
                    .isEqualTo(firstRevokedAt);
            softly.then(refreshTokenJpaRepository.findById(issuedAfterDisable.getId())
                            .orElseThrow().isRevoked())
                    .isFalse();
            softly.then(auditJpaRepository.count()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("ACTIVE 동일 상태 요청은 감사와 Refresh 폐기 없는 204")
    void treatsRepeatedActivationAsNoOp() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "active-noop-admin@example.com"
        );
        UUID targetId = api.signupSuccessfully("active-noop-target@example.com");
        api.loginSuccessfully("active-noop-target@example.com", PASSWORD);
        RefreshToken refreshToken = tokensFor(targetId).getFirst();

        // When
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "ACTIVE",
                        "활성 상태 확인"
                )
                .andExpect(status().isNoContent());

        // Then
        thenSoftly(softly -> {
            softly.then(accountJpaRepository.findById(targetId).orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.ACTIVE);
            softly.then(refreshTokenJpaRepository.findById(refreshToken.getId())
                            .orElseThrow().isRevoked())
                    .isFalse();
            softly.then(auditJpaRepository.count()).isZero();
        });
    }

    @Test
    @DisplayName("존재하지 않는 대상 계정의 관리자 상태 변경 거부")
    void rejectsMissingTargetAccount() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "missing-target-admin@example.com"
        );

        // When
        ResultActions response = api.changeAccountStatus(
                        administrator.accessToken(),
                        UUID.randomUUID(),
                        "DISABLED",
                        "존재하지 않는 대상"
                );

        // Then
        response
                .andExpectAll(
                        status().isNotFound(),
                        jsonPath("$.code").value("ACCOUNT_NOT_FOUND")
                );

        then(auditJpaRepository.count()).isZero();
    }

    @Test
    @DisplayName("허용하지 않는 목표 상태의 요청 본문 오류 처리")
    void rejectsMalformedTargetStatus() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "malformed-status-admin@example.com"
        );
        UUID targetId = api.signupSuccessfully("malformed-status-target@example.com");

        // When
        ResultActions response = api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "LOCKED",
                        "허용하지 않는 목표 상태"
                );

        // Then
        response
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code").value("COMMON_MALFORMED_REQUEST")
                );

        softlyAssertUnchangedTarget(targetId);
    }

    @Test
    @DisplayName("일반 사용자의 관리자 상태 변경 API 접근 거부")
    void rejectsNonAdministrator() throws Exception {
        // Given
        UUID targetId = api.signupSuccessfully("authorization-target@example.com");
        api.signupSuccessfully("ordinary@example.com");
        AuthApiTestClient.TokenBundle ordinaryUser = api.loginSuccessfully(
                "ordinary@example.com",
                PASSWORD
        );

        // When
        ResultActions response = api.changeAccountStatus(
                ordinaryUser.accessToken(),
                targetId,
                "DISABLED",
                "권한 없는 요청"
        );

        // Then
        response
                .andExpectAll(
                        status().isForbidden(),
                        jsonPath("$.code").value("AUTH_ACCESS_DENIED")
                );
    }

    @Test
    @DisplayName("관리자 상태 변경 API의 Access JWT 누락 거부")
    void rejectsMissingAdministratorAccessToken() throws Exception {
        // Given
        UUID targetId = api.signupSuccessfully("missing-admin-token-target@example.com");

        // When
        ResultActions response = mockMvc.perform(patch(
                                "/api/v1/admin/accounts/{user-id}/status",
                                targetId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED",
                                  "reason": "권한 없는 요청"
                                }
                                """));

        // Then
        response
                .andExpectAll(
                        status().isUnauthorized(),
                        jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED")
                );
    }

    @Test
    @DisplayName("DB에서 비활성화된 관리자의 기존 JWT 작업 거부")
    void rejectsStaleAdministratorJwtAfterDisable() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "stale-admin@example.com"
        );
        UUID targetId = api.signupSuccessfully("stale-target@example.com");
        fixture.changeStatus(administrator.userId(), AccountStatus.DISABLED);

        // When
        ResultActions response = api.changeAccountStatus(
                administrator.accessToken(),
                targetId,
                "ACTIVE",
                "오래된 JWT 요청"
        );

        // Then
        response
                .andExpectAll(
                        status().isForbidden(),
                        jsonPath("$.code")
                                .value("ACCOUNT_ADMIN_OPERATION_NOT_ALLOWED")
                );
        thenSoftly(softly -> {
            softly.then(accountJpaRepository.findById(targetId).orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.ACTIVE);
            softly.then(auditJpaRepository.count()).isZero();
        });
    }

    @Test
    @DisplayName("DB에서 역할이 회수된 관리자의 기존 JWT 작업 거부")
    void rejectsStaleAdministratorJwtAfterRoleDemotion() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "demoted-admin@example.com"
        );
        UUID targetId = api.signupSuccessfully("demoted-admin-target@example.com");
        fixture.changeGlobalRole(administrator.userId(), GlobalRole.USER);

        // When
        ResultActions response = api.changeAccountStatus(
                administrator.accessToken(),
                targetId,
                "ACTIVE",
                "오래된 JWT 요청"
        );

        // Then
        response
                .andExpectAll(
                        status().isForbidden(),
                        jsonPath("$.code")
                                .value("ACCOUNT_ADMIN_OPERATION_NOT_ALLOWED")
                );
        softlyAssertUnchangedTarget(targetId);
    }

    @Test
    @DisplayName("상태 변경 사유는 정규화 후 1~500자 계약")
    void validatesNormalizedAdministrativeReason() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "reason-admin@example.com"
        );
        UUID targetId = api.signupSuccessfully("reason-target@example.com");

        // When: 잘못된 사유들과 경계값 사유의 상태 변경 요청
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "DISABLED",
                        "   "
                )
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code")
                                .value("ACCOUNT_STATUS_CHANGE_INVALID_REASON")
                );
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "DISABLED",
                        "유효하지 않은\\u0000사유"
                )
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code")
                                .value("ACCOUNT_STATUS_CHANGE_INVALID_REASON")
                );
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "DISABLED",
                        "가".repeat(501)
                )
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$.code")
                                .value("ACCOUNT_STATUS_CHANGE_INVALID_REASON")
                );

        String acceptedReason = "나".repeat(500);
        api.changeAccountStatus(
                        administrator.accessToken(),
                        targetId,
                        "DISABLED",
                        "  " + acceptedReason + "  "
                )
                .andExpect(status().isNoContent());

        // Then
        thenSoftly(softly -> {
            softly.then(auditJpaRepository.findAll()).hasSize(1);
            softly.then(auditJpaRepository.findAll().getFirst().getReason())
                    .isEqualTo(acceptedReason);
        });
    }

    @Test
    @DisplayName("SYSTEM_ADMIN 자기 비활성화 거부")
    void rejectsSelfDisable() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "self-disable-admin@example.com"
        );

        // When
        ResultActions response = api.changeAccountStatus(
                administrator.accessToken(),
                administrator.userId(),
                "DISABLED",
                "자기 비활성화"
        );

        // Then
        response
                .andExpectAll(
                        status().isConflict(),
                        jsonPath("$.code").value("ACCOUNT_SELF_DISABLE_NOT_ALLOWED")
                );
    }

    @Test
    @DisplayName("마지막 이용 가능 SYSTEM_ADMIN 본인 탈퇴 거부")
    void rejectsLastAdministratorWithdrawal() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "last-admin@example.com"
        );

        // When
        ResultActions response = api.withdraw(administrator.accessToken(), PASSWORD);

        // Then
        response
                .andExpectAll(
                        status().isConflict(),
                        jsonPath("$.code").value("ACCOUNT_LAST_SYSTEM_ADMIN")
                );
        thenSoftly(softly -> {
            softly.then(accountJpaRepository.findById(administrator.userId())
                            .orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.ACTIVE);
            softly.then(tokensFor(administrator.userId())).hasSize(1).allSatisfy(token ->
                    softly.then(token.isRevoked()).isFalse()
            );
        });
    }

    @Test
    @DisplayName("LOCKED 관리자도 이용 가능한 관리자로 계산해 다른 관리자 탈퇴 허용")
    void countsLockedAdministratorAsUsable() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle withdrawingAdministrator = createAdministrator(
                "withdrawing-admin@example.com"
        );
        AuthApiTestClient.TokenBundle lockedAdministrator = createAdministrator(
                "locked-admin@example.com"
        );
        fixture.changeStatus(lockedAdministrator.userId(), AccountStatus.LOCKED);

        // When
        api.withdraw(withdrawingAdministrator.accessToken(), PASSWORD)
                .andExpect(status().isNoContent());

        // Then
        thenSoftly(softly -> {
            softly.then(accountJpaRepository.findById(withdrawingAdministrator.userId())
                            .orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.WITHDRAWN);
            softly.then(accountJpaRepository.findById(lockedAdministrator.userId())
                            .orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.LOCKED);
        });
    }

    @Test
    @DisplayName("WITHDRAWN 계정의 관리자 상태 변경 거부")
    void rejectsAdministrativeTransitionFromWithdrawnAccount() throws Exception {
        // Given
        AuthApiTestClient.TokenBundle administrator = createAdministrator(
                "withdrawn-target-admin@example.com"
        );
        UUID targetId = api.signupSuccessfully("withdrawn-target@example.com");
        fixture.changeStatus(targetId, AccountStatus.WITHDRAWN);

        // When
        ResultActions response = api.changeAccountStatus(
                administrator.accessToken(),
                targetId,
                "ACTIVE",
                "복구 시도"
        );

        // Then
        response
                .andExpectAll(
                        status().isConflict(),
                        jsonPath("$.code")
                                .value("ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED")
                );
    }

    private AuthApiTestClient.TokenBundle createAdministrator(String email) throws Exception {
        UUID accountId = api.signupSuccessfully(email);
        fixture.changeGlobalRole(accountId, GlobalRole.SYSTEM_ADMIN);
        return api.loginSuccessfully(email, PASSWORD);
    }

    private List<RefreshToken> tokensFor(UUID accountId) {
        return refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getAccountId().equals(accountId))
                .toList();
    }

    private void softlyAssertUnchangedTarget(UUID targetId) {
        thenSoftly(softly -> {
            softly.then(accountJpaRepository.findById(targetId).orElseThrow().getStatus())
                    .isEqualTo(AccountStatus.ACTIVE);
            softly.then(auditJpaRepository.count()).isZero();
        });
    }

    private void cleanDatabase() {
        auditJpaRepository.deleteAll();
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }
}
