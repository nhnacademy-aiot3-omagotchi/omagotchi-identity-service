package site.omagotchi.identityservice.email.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String CHALLENGE_ID = "challenge-id";
    private static final String CODE = "042910";
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration COOLDOWN = Duration.ofMinutes(1);

    @Mock
    private EmailVerificationRepository verificationRepository;

    @Mock
    private VerificationCodeGenerator codeGenerator;

    @Mock
    private VerificationMailDispatchService mailDispatchService;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
                verificationRepository,
                codeGenerator,
                mailDispatchService,
                new EmailVerificationProperties(CODE_TTL, COOLDOWN, 5)
        );
    }

    @Test
    @DisplayName("새 OTP Challenge를 저장하고 메일 발송")
    void requestsChallenge() {
        given(verificationRepository.acquireCooldown(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                COOLDOWN
        )).willReturn(true);
        given(codeGenerator.generate()).willReturn(CODE);
        ArgumentCaptor<OtpChallenge> challengeCaptor =
                ArgumentCaptor.forClass(OtpChallenge.class);

        EmailVerificationChallengeResult result = service.requestCode(
                EMAIL,
                VerificationPurpose.SIGN_UP
        );

        verify(verificationRepository).replaceChallenge(
                eq(VerificationPurpose.SIGN_UP),
                eq(EMAIL),
                challengeCaptor.capture(),
                eq(CODE_TTL)
        );
        OtpChallenge challenge = challengeCaptor.getValue();
        verify(mailDispatchService).dispatch(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CODE,
                challenge.challengeId(),
                CODE_TTL
        );
        thenSoftly(softly -> {
            softly.then(challenge.code()).isEqualTo(CODE);
            softly.then(challenge.challengeId()).matches("[0-9a-f-]{36}");
            softly.then(result.challengeId()).isEqualTo(challenge.challengeId());
            softly.then(result.expiresInSeconds()).isEqualTo(600L);
        });
    }

    @Test
    @DisplayName("사용자 재발급은 새 OTP와 새 Challenge ID 생성")
    void createsNewOtpAndChallengeIdForUserReissue() {
        given(verificationRepository.acquireCooldown(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                COOLDOWN
        )).willReturn(true);
        given(codeGenerator.generate()).willReturn("111111", "222222");
        ArgumentCaptor<OtpChallenge> challengeCaptor =
                ArgumentCaptor.forClass(OtpChallenge.class);

        EmailVerificationChallengeResult first = service.requestCode(
                EMAIL,
                VerificationPurpose.SIGN_UP
        );
        EmailVerificationChallengeResult second = service.requestCode(
                EMAIL,
                VerificationPurpose.SIGN_UP
        );

        verify(verificationRepository, times(2)).replaceChallenge(
                eq(VerificationPurpose.SIGN_UP),
                eq(EMAIL),
                challengeCaptor.capture(),
                eq(CODE_TTL)
        );
        thenSoftly(softly -> {
            softly.then(first.challengeId()).isNotEqualTo(second.challengeId());
            softly.then(challengeCaptor.getAllValues())
                    .extracting(OtpChallenge::code)
                    .containsExactly("111111", "222222");
        });
    }

    @Test
    @DisplayName("재발송 대기 중이면 남은 시간과 함께 요청 제한")
    void rejectsRequestDuringCooldown() {
        given(verificationRepository.acquireCooldown(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                COOLDOWN
        )).willReturn(false);
        given(verificationRepository.remainingCooldownSeconds(
                VerificationPurpose.SIGN_UP,
                EMAIL
        )).willReturn(42L);

        Throwable thrown = catchThrowable(() -> service.requestCode(
                EMAIL,
                VerificationPurpose.SIGN_UP
        ));

        then(thrown).isInstanceOfSatisfying(
                EmailVerificationCooldownException.class,
                exception -> then(exception.retryAfterSeconds()).isEqualTo(42L)
        );
        verifyNoInteractions(codeGenerator, mailDispatchService);
    }

    @Test
    @DisplayName("올바른 Challenge와 OTP를 원자적으로 소비")
    void verifiesAndConsumesCode() {
        given(verificationRepository.verifyAndConsume(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE_ID,
                CODE,
                5
        )).willReturn(OtpVerificationStatus.VERIFIED);

        service.verifyAndConsumeCode(
                EMAIL,
                VerificationPurpose.SIGN_UP,
                CHALLENGE_ID,
                CODE
        );

        verify(verificationRepository).verifyAndConsume(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE_ID,
                CODE,
                5
        );
    }

    @ParameterizedTest
    @EnumSource(value = OtpVerificationStatus.class, names = {"INVALID", "EXHAUSTED"})
    @DisplayName("잘못되거나 실패 제한을 초과한 OTP는 일반 인증 오류")
    void rejectsInvalidChallenge(OtpVerificationStatus status) {
        given(verificationRepository.verifyAndConsume(
                VerificationPurpose.PASSWORD_CHANGE,
                EMAIL,
                CHALLENGE_ID,
                "999999",
                5
        )).willReturn(status);

        Throwable thrown = catchThrowable(() -> service.verifyAndConsumeCode(
                EMAIL,
                VerificationPurpose.PASSWORD_CHANGE,
                CHALLENGE_ID,
                "999999"
        ));

        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isSameAs(EmailVerificationErrorCode.INVALID)
        );
    }

    @Test
    @DisplayName("비동기 작업 접수 실패 시 해당 Challenge만 삭제")
    void deletesOnlyCurrentChallengeWhenDispatchIsRejected() {
        given(verificationRepository.acquireCooldown(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                COOLDOWN
        )).willReturn(true);
        given(codeGenerator.generate()).willReturn(CODE);
        willThrow(new RejectedExecutionException("queue-full"))
                .given(mailDispatchService)
                .dispatch(
                        eq(VerificationPurpose.SIGN_UP),
                        eq(EMAIL),
                        eq(CODE),
                        any(),
                        eq(CODE_TTL)
                );

        Throwable thrown = catchThrowable(() -> service.requestCode(
                EMAIL,
                VerificationPurpose.SIGN_UP
        ));

        then(thrown).isInstanceOf(RejectedExecutionException.class);
        verify(verificationRepository).deleteChallengeIfMatches(
                eq(VerificationPurpose.SIGN_UP),
                eq(EMAIL),
                any()
        );
    }

    @Test
    @DisplayName("올바르지 않은 이메일 형식은 비즈니스 예외로 Redis 접근 전 거절")
    void rejectsInvalidEmailBeforeRepositoryAccess() {
        Throwable thrown = catchThrowable(() -> service.requestCode(
                "invalid-email",
                VerificationPurpose.SIGN_UP
        ));

        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isSameAs(EmailVerificationErrorCode.INVALID_EMAIL)
        );
        verifyNoInteractions(
                verificationRepository,
                codeGenerator,
                mailDispatchService
        );
    }
}
