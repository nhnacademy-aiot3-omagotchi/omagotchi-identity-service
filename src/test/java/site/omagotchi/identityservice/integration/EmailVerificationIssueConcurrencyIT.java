package site.omagotchi.identityservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class})
class EmailVerificationIssueConcurrencyIT {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Instant STARTED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailVerificationMailSender mailSender;
    @MockitoBean
    private Clock clock;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("메일 응답 중 대체된 Challenge는 성공 응답하지 않음")
    void doesNotAcceptChallengeSupersededWhileWaitingForDelivery() throws Exception {
        // Given
        String email = "superseded-delivery+" + UUID.randomUUID() + "@example.com";
        AtomicReference<Instant> now = new AtomicReference<>(STARTED_AT);
        AtomicInteger deliverySequence = new AtomicInteger();
        AtomicReference<UUID> firstChallengeId = new AtomicReference<>();
        CountDownLatch firstDeliveryStarted = new CountDownLatch(1);
        CountDownLatch allowFirstDeliveryResponse = new CountDownLatch(1);

        doAnswer(invocation -> now.get()).when(clock).instant();
        doAnswer(invocation -> {
            if (deliverySequence.incrementAndGet() == 1) {
                firstChallengeId.set(invocation.getArgument(0));
                firstDeliveryStarted.countDown();
                await(allowFirstDeliveryResponse);
            }
            return null;
        }).when(mailSender).sendVerificationCode(
                any(),
                eq(email),
                any(),
                eq(Duration.ofMinutes(5))
        );

        Future<MvcResult> firstIssue = executor.submit(() -> issueSignupOtp(email));
        await(firstDeliveryStarted);
        now.set(STARTED_AT.plus(Duration.ofMinutes(1)));

        MvcResult secondIssue;
        try {
            // When
            secondIssue = issueSignupOtp(email);
        } finally {
            allowFirstDeliveryResponse.countDown();
        }
        MvcResult staleFirstIssue = firstIssue.get(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );

        // Then
        then(secondIssue.getResponse().getStatus()).isEqualTo(202);
        then(staleFirstIssue.getResponse().getStatus()).isEqualTo(409);
        then(objectMapper.readTree(staleFirstIssue.getResponse().getContentAsString())
                .get("code").asString())
                .isEqualTo("EMAIL_VERIFICATION_ISSUE_SUPERSEDED");

        Map<String, Object> firstChallenge = jdbcTemplate.queryForMap(
                """
                SELECT status, delivery_status
                FROM identity_service.email_verification_challenges
                WHERE id = ?
                """,
                firstChallengeId.get()
        );
        then(firstChallenge)
                .containsEntry("status", "SUPERSEDED")
                .containsEntry("delivery_status", "ACCEPTED");
    }

    private MvcResult issueSignupOtp(String email) throws Exception {
        return mockMvc.perform(post("/api/v2/auth/signup/email-otp")
                        .with(httpBasic(
                                AuthApiTestClient.FRONTEND_USERNAME,
                                AuthApiTestClient.FRONTEND_PASSWORD
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password-passphrase",
                                "name", "member"
                        ))))
                .andReturn();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과됐습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단됐습니다.", exception);
        }
    }
}
