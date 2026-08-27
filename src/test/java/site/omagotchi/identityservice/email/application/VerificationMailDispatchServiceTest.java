package site.omagotchi.identityservice.email.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.email.application.port.EmailDeliveryException;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.application.port.VerificationMailSender;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;

import java.time.Duration;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationMailDispatchServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String CODE = "042910";
    private static final String CHALLENGE_ID = "challenge-id";
    private static final Duration VALIDITY = Duration.ofMinutes(10);

    @Mock
    private VerificationMailSender mailSender;

    @Mock
    private EmailVerificationRepository repository;

    private VerificationMailDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new VerificationMailDispatchService(mailSender, repository);
    }

    @Test
    @DisplayName("메일 발송 성공 시 저장한 Challenge 유지")
    void keepsChallengeWhenDeliverySucceeds() {
        dispatchService.dispatch(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CODE,
                CHALLENGE_ID,
                VALIDITY
        );

        verify(mailSender).sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);
        verify(repository, never()).deleteChallengeIfMatches(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE_ID
        );
    }

    @Test
    @DisplayName("timeout 또는 5xx는 동일 Challenge로 최대 두 번 기술적 재시도")
    void retriesSameChallengeForTechnicalFailure() {
        EmailDeliveryException failure =
                new EmailDeliveryException(503, "temporary_error", true);
        doThrow(failure)
                .doThrow(failure)
                .doNothing()
                .when(mailSender)
                .sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);

        dispatchService.dispatch(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CODE,
                CHALLENGE_ID,
                VALIDITY
        );

        verify(mailSender, times(3)).sendVerificationCode(
                EMAIL,
                CODE,
                CHALLENGE_ID,
                VALIDITY
        );
        verify(repository, never()).deleteChallengeIfMatches(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE_ID
        );
    }

    @Test
    @DisplayName("재시도 불가능한 오류는 해당 Challenge만 즉시 삭제")
    void deletesMatchingChallengeForNonRetryableFailure() {
        doThrow(new EmailDeliveryException(400, "validation_error", false))
                .when(mailSender)
                .sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);
        when(repository.deleteChallengeIfMatches(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE_ID
        )).thenReturn(true);

        dispatchService.dispatch(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CODE,
                CHALLENGE_ID,
                VALIDITY
        );

        verify(mailSender).sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);
        verify(repository).deleteChallengeIfMatches(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE_ID
        );
    }

    @Test
    @DisplayName("두 번의 기술적 재시도까지 실패하면 해당 Challenge만 삭제")
    void deletesMatchingChallengeAfterRetriesAreExhausted() {
        EmailDeliveryException failure =
                new EmailDeliveryException(503, "temporary_error", true);
        doThrow(failure)
                .when(mailSender)
                .sendVerificationCode(EMAIL, CODE, CHALLENGE_ID, VALIDITY);
        when(repository.deleteChallengeIfMatches(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE_ID
        )).thenReturn(false);

        dispatchService.dispatch(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CODE,
                CHALLENGE_ID,
                VALIDITY
        );

        verify(mailSender, times(3)).sendVerificationCode(
                EMAIL,
                CODE,
                CHALLENGE_ID,
                VALIDITY
        );
        verify(repository).deleteChallengeIfMatches(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE_ID
        );
    }
}
