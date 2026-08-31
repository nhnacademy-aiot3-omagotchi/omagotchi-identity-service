package site.omagotchi.identityservice.emailverification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationUseServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final String EMAIL = "member@example.com";
    private static final UUID CHALLENGE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000701201"
    );
    private static final UUID SCOPE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000701202"
    );

    @Mock
    private EmailVerificationRepository repository;
    @Mock
    private VerificationCodeAuthenticator authenticator;

    private EmailVerificationUseService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationUseService(
                repository,
                authenticator,
                new EmailVerificationProperties(
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(1),
                        2,
                        "test-hmac-secret-with-at-least-32-characters"
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("올바른 문맥과 인증번호 검증 성공")
    void verifiesMatchingCode() {
        // Given
        EmailVerificationChallenge challenge = challenge();
        given(repository.lockChallenge(challenge.getId())).willReturn(Optional.of(challenge));
        given(authenticator.matches(
                challenge.getCodeMac(),
                challenge.getId(),
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                "123456"
        )).willReturn(true);

        // When
        boolean verified = service.verify(
                challenge.getId(),
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                "123456"
        );

        // Then
        then(verified).isTrue();
        then(challenge.getFailedAttempts()).isZero();
    }

    @Test
    @DisplayName("잘못된 인증번호 실패 횟수를 누적하고 최대 횟수에서 소진")
    void recordsAndExhaustsInvalidCode() {
        // Given
        EmailVerificationChallenge challenge = challenge();
        given(repository.lockChallenge(challenge.getId())).willReturn(Optional.of(challenge));
        given(authenticator.matches(
                challenge.getCodeMac(),
                challenge.getId(),
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                "000000"
        )).willReturn(false);

        // When
        then(service.verify(
                challenge.getId(), EMAIL, EmailVerificationPurpose.SIGNUP, "000000"
        )).isFalse();
        then(service.verify(
                challenge.getId(), EMAIL, EmailVerificationPurpose.SIGNUP, "000000"
        )).isFalse();

        // Then
        then(challenge.getFailedAttempts()).isEqualTo((short) 2);
        then(challenge.getStatus()).isEqualTo(EmailVerificationStatus.EXHAUSTED);
    }

    @Test
    @DisplayName("다른 이메일 문맥은 Challenge를 변경하지 않고 거부")
    void rejectsDifferentContextWithoutConsumingAttempts() {
        // Given
        EmailVerificationChallenge challenge = challenge();
        given(repository.lockChallenge(challenge.getId())).willReturn(Optional.of(challenge));

        // When
        boolean verified = service.verify(
                challenge.getId(),
                "other@example.com",
                EmailVerificationPurpose.SIGNUP,
                "123456"
        );

        // Then
        then(verified).isFalse();
        then(challenge.getFailedAttempts()).isZero();
        verify(authenticator, never()).matches(
                challenge.getCodeMac(),
                challenge.getId(),
                "other@example.com",
                EmailVerificationPurpose.SIGNUP,
                "123456"
        );
    }

    @Test
    @DisplayName("검증된 Challenge 소비")
    void consumesChallenge() {
        // Given
        EmailVerificationChallenge challenge = challenge();
        given(repository.lockChallenge(challenge.getId())).willReturn(Optional.of(challenge));

        // When
        service.consume(challenge.getId());

        // Then
        then(challenge.getStatus()).isEqualTo(EmailVerificationStatus.CONSUMED);
    }

    private EmailVerificationChallenge challenge() {
        return EmailVerificationChallenge.issue(
                CHALLENGE_ID,
                SCOPE_ID,
                EMAIL,
                EmailVerificationPurpose.SIGNUP,
                "a".repeat(64),
                NOW.plusSeconds(300),
                NOW.minusSeconds(1)
        );
    }
}
