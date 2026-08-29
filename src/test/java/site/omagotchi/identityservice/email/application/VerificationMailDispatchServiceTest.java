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
        dispatchService = new VerificationMailDispatchService(
                mailSender,
                repository
        );
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
    @DisplayName("메일 발송 최종 실패 시 해당 Challenge 삭제")
    void deletesMatchingChallengeWhenDeliveryFails() {
        doThrow(new EmailDeliveryException(500, "internal_server_error", false))
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
}
