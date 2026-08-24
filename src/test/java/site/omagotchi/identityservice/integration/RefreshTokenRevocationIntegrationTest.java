package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.identityservice.account.infrastructure.AccountJpaRepository;
import site.omagotchi.identityservice.auth.application.RefreshSessionRevocationReason;
import site.omagotchi.identityservice.auth.application.RefreshTokenRevocationService;
import site.omagotchi.identityservice.auth.domain.RefreshToken;
import site.omagotchi.identityservice.auth.domain.RefreshTokenRevocationReason;
import site.omagotchi.identityservice.auth.infrastructure.RefreshTokenJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class RefreshTokenRevocationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private RefreshTokenRevocationService refreshTokenRevocationService;

    private AuthApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new AuthApiTestClient(mockMvc, objectMapper);
        refreshTokenJpaRepository.deleteAll();
        accountJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("사용자의 모든 Refresh Token Family 폐기와 다른 사용자 격리")
    void revokesAllFamiliesForOnlyTargetAccount() throws Exception {
        // Given
        UUID targetAccountId = api.signupSuccessfully("target@example.com");
        AuthApiTestClient.TokenBundle firstFamily = api.loginSuccessfully(
                "target@example.com",
                "password-passphrase"
        );
        AuthApiTestClient.TokenBundle rotatedFirstFamily = api.refreshSuccessfully(
                firstFamily.refreshToken()
        );
        AuthApiTestClient.TokenBundle secondFamily = api.loginSuccessfully(
                "target@example.com",
                "password-passphrase"
        );

        UUID otherAccountId = api.signupSuccessfully("other@example.com");
        AuthApiTestClient.TokenBundle otherFamily = api.loginSuccessfully(
                "other@example.com",
                "password-passphrase"
        );

        // When
        refreshTokenRevocationService.revokeAllForAccount(
                targetAccountId,
                RefreshSessionRevocationReason.PASSWORD_CHANGED
        );
        Map<Long, RevocationState> firstRevocationStates = revocationStates(
                tokensFor(targetAccountId)
        );

        // 다른 사유의 반복 호출에도 최초 폐기 상태를 보존하는 멱등 경계
        refreshTokenRevocationService.revokeAllForAccount(
                targetAccountId,
                RefreshSessionRevocationReason.ACCOUNT_DISABLED
        );

        List<RefreshToken> targetTokens = tokensFor(targetAccountId);
        List<RefreshToken> otherTokens = tokensFor(otherAccountId);

        // Then
        api.refresh(rotatedFirstFamily.refreshToken()).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        api.refresh(secondFamily.refreshToken()).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN")
        );
        AuthApiTestClient.TokenBundle refreshedOther = api.refreshSuccessfully(
                otherFamily.refreshToken()
        );

        thenSoftly(softly -> {
            softly.then(targetTokens).hasSize(3);
            softly.then(targetTokens).allSatisfy(token -> {
                RevocationState firstState = firstRevocationStates.get(token.getId());

                softly.then(firstState).isNotNull();
                softly.then(token.isRevoked()).isTrue();
                softly.then(token.getRevokedAt()).isEqualTo(firstState.revokedAt());
                softly.then(token.getRevocationReason())
                        .isEqualTo(firstState.reason())
                        .isEqualTo(RefreshTokenRevocationReason.PASSWORD_CHANGED);
            });
            softly.then(otherTokens).hasSize(1);
            softly.then(otherTokens.getFirst().isRevoked()).isFalse();
            softly.then(refreshedOther.userId()).isEqualTo(otherAccountId);
        });
    }

    private List<RefreshToken> tokensFor(UUID accountId) {
        return refreshTokenJpaRepository.findAll().stream()
                .filter(token -> token.getAccountId().equals(accountId))
                .toList();
    }

    private Map<Long, RevocationState> revocationStates(List<RefreshToken> tokens) {
        return tokens.stream().collect(Collectors.toMap(
                RefreshToken::getId,
                token -> new RevocationState(
                        token.getRevokedAt(),
                        token.getRevocationReason()
                )
        ));
    }

    private record RevocationState(
            Instant revokedAt,
            RefreshTokenRevocationReason reason
    ) {
    }
}
