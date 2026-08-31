package site.omagotchi.identityservice.emailverification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationMailSender;
import site.omagotchi.identityservice.emailverification.application.result.IssuedEmailVerification;
import site.omagotchi.identityservice.emailverification.application.result.PreparedEmailVerification;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;
import site.omagotchi.identityservice.global.exception.DependencyUnavailableException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationIssueServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final String EMAIL = "member@example.com";
    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000701001"
    );

    @Mock
    private EmailVerificationIssuanceTransaction issuanceTransaction;
    @Mock
    private EmailVerificationDeliveryTransaction deliveryTransaction;
    @Mock
    private EmailVerificationMailSender mailSender;

    private EmailVerificationIssueService service;
    private PreparedEmailVerification prepared;

    @BeforeEach
    void setUp() {
        EmailVerificationProperties properties = new EmailVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                5,
                "test-hmac-secret-with-at-least-32-characters"
        );
        service = new EmailVerificationIssueService(
                issuanceTransaction,
                deliveryTransaction,
                mailSender,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        prepared = new PreparedEmailVerification(
                CHALLENGE_ID,
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                "123456",
                NOW.plusSeconds(300)
        );
        given(issuanceTransaction.prepare(EMAIL, EmailVerificationPurpose.SIGNUP))
                .willReturn(prepared);
    }

    @Test
    @DisplayName("DB Commit 뒤 동기 메일 전송과 ACCEPTED 상태 기록")
    void sendsAndMarksAccepted() {
        // Given
        given(deliveryTransaction.markAccepted(prepared)).willReturn(true);

        // When
        IssuedEmailVerification issued = service.issue(EMAIL, EmailVerificationPurpose.SIGNUP);

        // Then
        then(issued.challengeId()).isEqualTo(prepared.challengeId());
        then(issued.expiresInSeconds()).isEqualTo(300);
        verify(mailSender).sendVerificationCode(
                prepared.challengeId(), EMAIL, "123456", Duration.ofMinutes(5)
        );
        verify(deliveryTransaction).markAccepted(prepared);
    }

    @Test
    @DisplayName("메일 실패 시 FAILED 상태와 쿨다운 보상 후 503 예외")
    void compensatesDeliveryFailure() {
        // Given
        EmailDeliveryException deliveryFailure = new EmailDeliveryException(
                "delivery failed",
                new IllegalStateException("provider unavailable")
        );
        willThrow(deliveryFailure).given(mailSender).sendVerificationCode(
                prepared.challengeId(), EMAIL, "123456", Duration.ofMinutes(5)
        );

        // When
        // Then
        thenThrownBy(() -> service.issue(EMAIL, EmailVerificationPurpose.SIGNUP))
                .isInstanceOfSatisfying(
                        DependencyUnavailableException.class,
                        exception -> then(exception.getErrorCode())
                                .isEqualTo(EmailVerificationErrorCode.DELIVERY_UNAVAILABLE)
                );
        verify(deliveryTransaction).markFailedAndReleaseCooldown(prepared);
    }

    @Test
    @DisplayName("Provider 429는 기존 쿨다운을 유지하고 남은 시간을 응답")
    void keepsCooldownAfterProviderRateLimit() {
        // Given
        EmailDeliveryException deliveryFailure = EmailDeliveryException.rateLimited(
                "provider rate limited",
                new IllegalStateException("provider unavailable")
        );
        willThrow(deliveryFailure).given(mailSender).sendVerificationCode(
                prepared.challengeId(), EMAIL, "123456", Duration.ofMinutes(5)
        );
        given(deliveryTransaction.markFailedKeepingCooldown(prepared)).willReturn(42L);

        // When
        // Then
        thenThrownBy(() -> service.issue(EMAIL, EmailVerificationPurpose.SIGNUP))
                .isInstanceOfSatisfying(
                        EmailVerificationCooldownException.class,
                        exception -> then(exception.retryAfterSeconds()).isEqualTo(42)
                );
        verify(deliveryTransaction).markFailedKeepingCooldown(prepared);
        verify(deliveryTransaction, never()).markFailedAndReleaseCooldown(prepared);
    }

    @Test
    @DisplayName("메일 접수 후 상태 기록 실패여도 PENDING Challenge와 202 결과 유지")
    void keepsAcceptedResultWhenStatusRecordFails() {
        // Given
        willThrow(new IllegalStateException("database unavailable"))
                .given(deliveryTransaction).markAccepted(prepared);

        // When
        IssuedEmailVerification issued = service.issue(EMAIL, EmailVerificationPurpose.SIGNUP);

        // Then
        then(issued.challengeId()).isEqualTo(prepared.challengeId());
    }

    @Test
    @DisplayName("메일 응답 중 대체된 Challenge는 409 예외")
    void rejectsSupersededChallengeAfterDelivery() {
        // Given
        given(deliveryTransaction.markAccepted(prepared)).willReturn(false);

        // When
        // Then
        thenThrownBy(() -> service.issue(EMAIL, EmailVerificationPurpose.SIGNUP))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> then(exception.getErrorCode())
                                .isEqualTo(EmailVerificationErrorCode.ISSUE_SUPERSEDED)
                );
    }

    @Test
    @DisplayName("메일 처리 중 Challenge가 만료되면 쿨다운 해제 후 503 응답")
    void rejectsExpiredChallengeAfterDelivery() {
        // Given
        given(deliveryTransaction.markAccepted(prepared)).willReturn(true);
        EmailVerificationProperties properties = new EmailVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                5,
                "test-hmac-secret-with-at-least-32-characters"
        );
        service = new EmailVerificationIssueService(
                issuanceTransaction,
                deliveryTransaction,
                mailSender,
                properties,
                Clock.fixed(prepared.expiresAt(), ZoneOffset.UTC)
        );

        // When
        // Then
        thenThrownBy(() -> service.issue(EMAIL, EmailVerificationPurpose.SIGNUP))
                .isInstanceOfSatisfying(
                        DependencyUnavailableException.class,
                        exception -> then(exception.getErrorCode())
                                .isEqualTo(EmailVerificationErrorCode.DELIVERY_UNAVAILABLE)
                );
        verify(deliveryTransaction).markAccepted(prepared);
        verify(deliveryTransaction).releaseCooldown(prepared);
    }
}
