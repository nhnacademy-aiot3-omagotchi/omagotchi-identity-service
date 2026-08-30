package site.omagotchi.identityservice.email.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.application.port.EmailVerificationStorageException;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Duration;

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

    @Nested
    @DisplayName("인증 코드 요청")
    class RequestCodeTest {

        @Test
        @DisplayName("새 Challenge를 저장하고 메일을 발송한다")
        void requestsChallenge() {
            // Given
            given(codeGenerator.generate()).willReturn(CODE);
            givenReservation(EmailVerificationReservationResult.acquired());
            ArgumentCaptor<OtpChallenge> challengeCaptor =
                    ArgumentCaptor.forClass(OtpChallenge.class);

            // When
            EmailVerificationChallengeResult result = service.requestCode(
                    EMAIL,
                    VerificationPurpose.SIGN_UP
            );

            // Then
            verify(verificationRepository).reserveChallenge(
                    eq(VerificationPurpose.SIGN_UP),
                    eq(EMAIL),
                    challengeCaptor.capture(),
                    eq(CODE_TTL),
                    eq(COOLDOWN)
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
        @DisplayName("재발급하면 새 OTP와 Challenge ID를 생성한다")
        void createsNewOtpAndChallengeIdForUserReissue() {
            // Given
            given(codeGenerator.generate()).willReturn("111111", "222222");
            givenReservation(EmailVerificationReservationResult.acquired());
            ArgumentCaptor<OtpChallenge> challengeCaptor =
                    ArgumentCaptor.forClass(OtpChallenge.class);

            // When
            EmailVerificationChallengeResult first = service.requestCode(
                    EMAIL,
                    VerificationPurpose.SIGN_UP
            );
            EmailVerificationChallengeResult second = service.requestCode(
                    EMAIL,
                    VerificationPurpose.SIGN_UP
            );

            // Then
            verify(verificationRepository, times(2)).reserveChallenge(
                    eq(VerificationPurpose.SIGN_UP),
                    eq(EMAIL),
                    challengeCaptor.capture(),
                    eq(CODE_TTL),
                    eq(COOLDOWN)
            );
            thenSoftly(softly -> {
                softly.then(first.challengeId()).isNotEqualTo(second.challengeId());
                softly.then(challengeCaptor.getAllValues())
                        .extracting(OtpChallenge::code)
                        .containsExactly("111111", "222222");
            });
        }

        @Test
        @DisplayName("재발송 대기 중이면 남은 시간과 함께 제한한다")
        void rejectsRequestDuringCooldown() {
            // Given
            given(codeGenerator.generate()).willReturn(CODE);
            givenReservation(EmailVerificationReservationResult.cooldown(42L));

            // When
            Throwable thrown = catchThrowable(() -> service.requestCode(
                    EMAIL,
                    VerificationPurpose.SIGN_UP
            ));

            // Then
            then(thrown).isInstanceOfSatisfying(
                    EmailVerificationCooldownException.class,
                    exception -> then(exception.retryAfterSeconds()).isEqualTo(42L)
            );
            verifyNoInteractions(mailDispatchService);
        }

        @Test
        @DisplayName("저장소 장애를 원인과 함께 가용성 오류로 변환한다")
        void translatesStorageFailureDuringChallengeRequest() {
            // Given
            IllegalStateException redisFailure = new IllegalStateException("Redis 연결 실패");
            EmailVerificationStorageException storageFailure =
                    new EmailVerificationStorageException(redisFailure);
            given(codeGenerator.generate()).willReturn(CODE);
            given(verificationRepository.reserveChallenge(
                    eq(VerificationPurpose.SIGN_UP),
                    eq(EMAIL),
                    any(OtpChallenge.class),
                    eq(CODE_TTL),
                    eq(COOLDOWN)
            )).willThrow(storageFailure);

            // When
            Throwable thrown = catchThrowable(() -> service.requestCode(
                    EMAIL,
                    VerificationPurpose.SIGN_UP
            ));

            // Then
            thenUnavailableWithOriginalCause(thrown, storageFailure, redisFailure);
            verifyNoInteractions(mailDispatchService);
        }

        @Test
        @DisplayName("정규화되지 않은 이메일은 저장소 접근 전에 거절한다")
        void rejectsNonNormalizedEmailBeforeRepositoryAccess() {
            // Given
            String nonNormalizedEmail = "  USER@Example.COM  ";

            // When
            Throwable thrown = catchThrowable(() -> service.requestCode(
                    nonNormalizedEmail,
                    VerificationPurpose.SIGN_UP
            ));

            // Then
            then(thrown)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("normalizedEmail은 정규화된 값이어야 합니다.");
            verifyNoInteractions(
                    verificationRepository,
                    codeGenerator,
                    mailDispatchService
            );
        }

        @Nested
        @DisplayName("메일 발송 접수 실패 처리")
        class DispatchFailureTest {

            @Test
            @DisplayName("해당 Challenge와 쿨다운만 삭제한다")
            void deletesOnlyCurrentReservationWhenDispatchIsRejected() {
                // Given
                given(codeGenerator.generate()).willReturn(CODE);
                givenReservation(EmailVerificationReservationResult.acquired());
                ArgumentCaptor<OtpChallenge> challengeCaptor =
                        ArgumentCaptor.forClass(OtpChallenge.class);
                TaskRejectedException rejection =
                        new TaskRejectedException("mailTaskExecutor queue-full");
                willThrow(rejection)
                        .given(mailDispatchService)
                        .dispatch(
                                eq(VerificationPurpose.SIGN_UP),
                                eq(EMAIL),
                                eq(CODE),
                                any(),
                                eq(CODE_TTL)
                        );

                // When
                Throwable thrown = catchThrowable(() -> service.requestCode(
                        EMAIL,
                        VerificationPurpose.SIGN_UP
                ));

                // Then
                then(thrown).isInstanceOfSatisfying(BusinessException.class, exception -> {
                    then(exception.getErrorCode())
                            .isSameAs(EmailVerificationErrorCode.UNAVAILABLE);
                    then(exception.getCause()).isSameAs(rejection);
                });
                verify(verificationRepository).reserveChallenge(
                        eq(VerificationPurpose.SIGN_UP),
                        eq(EMAIL),
                        challengeCaptor.capture(),
                        eq(CODE_TTL),
                        eq(COOLDOWN)
                );
                verify(verificationRepository).deleteChallengeAndCooldownIfMatches(
                        VerificationPurpose.SIGN_UP,
                        EMAIL,
                        challengeCaptor.getValue().challengeId()
                );
            }

            @Test
            @DisplayName("저장소 정리도 실패하면 두 원인을 모두 보존한다")
            void preservesDispatchAndStorageFailuresWhenCleanupFails() {
                // Given
                TaskRejectedException dispatchFailure =
                        new TaskRejectedException("mailTaskExecutor queue-full");
                IllegalStateException redisFailure =
                        new IllegalStateException("Redis 연결 실패");
                EmailVerificationStorageException storageFailure =
                        new EmailVerificationStorageException(redisFailure);
                given(codeGenerator.generate()).willReturn(CODE);
                givenReservation(EmailVerificationReservationResult.acquired());
                willThrow(dispatchFailure)
                        .given(mailDispatchService)
                        .dispatch(
                                eq(VerificationPurpose.SIGN_UP),
                                eq(EMAIL),
                                eq(CODE),
                                any(),
                                eq(CODE_TTL)
                        );
                willThrow(storageFailure)
                        .given(verificationRepository)
                        .deleteChallengeAndCooldownIfMatches(
                                eq(VerificationPurpose.SIGN_UP),
                                eq(EMAIL),
                                any()
                        );

                // When
                Throwable thrown = catchThrowable(() -> service.requestCode(
                        EMAIL,
                        VerificationPurpose.SIGN_UP
                ));

                // Then
                then(thrown).isInstanceOfSatisfying(BusinessException.class, exception -> {
                    then(exception.getErrorCode())
                            .isSameAs(EmailVerificationErrorCode.UNAVAILABLE);
                    then(exception.getCause()).isSameAs(dispatchFailure);
                    then(exception.getCause().getSuppressed())
                            .containsExactly(storageFailure);
                    then(storageFailure.getCause()).isSameAs(redisFailure);
                });
            }

            @Test
            @DisplayName("일반 오류는 가용성 오류로 오분류하지 않는다")
            void preservesUnexpectedDispatchFailureWhenCleanupFails() {
                // Given
                IllegalStateException dispatchFailure =
                        new IllegalStateException("비동기 프록시 설정 오류");
                EmailVerificationStorageException cleanupFailure =
                        new EmailVerificationStorageException(
                                new IllegalStateException("Redis 연결 실패")
                        );
                given(codeGenerator.generate()).willReturn(CODE);
                givenReservation(EmailVerificationReservationResult.acquired());
                willThrow(dispatchFailure)
                        .given(mailDispatchService)
                        .dispatch(
                                eq(VerificationPurpose.SIGN_UP),
                                eq(EMAIL),
                                eq(CODE),
                                any(),
                                eq(CODE_TTL)
                        );
                willThrow(cleanupFailure)
                        .given(verificationRepository)
                        .deleteChallengeAndCooldownIfMatches(
                                eq(VerificationPurpose.SIGN_UP),
                                eq(EMAIL),
                                any()
                        );

                // When
                Throwable thrown = catchThrowable(() -> service.requestCode(
                        EMAIL,
                        VerificationPurpose.SIGN_UP
                ));

                // Then
                then(thrown).isSameAs(dispatchFailure);
                then(thrown.getSuppressed()).containsExactly(cleanupFailure);
            }

            @Test
            @DisplayName("정리 중 예상 밖 오류는 숨기지 않는다")
            void propagatesUnexpectedFailureDuringDispatchCleanup() {
                // Given
                TaskRejectedException dispatchFailure =
                        new TaskRejectedException("mailTaskExecutor queue-full");
                IllegalStateException cleanupFailure =
                        new IllegalStateException("예상하지 못한 정리 실패");
                given(codeGenerator.generate()).willReturn(CODE);
                givenReservation(EmailVerificationReservationResult.acquired());
                willThrow(dispatchFailure)
                        .given(mailDispatchService)
                        .dispatch(
                                eq(VerificationPurpose.SIGN_UP),
                                eq(EMAIL),
                                eq(CODE),
                                any(),
                                eq(CODE_TTL)
                        );
                willThrow(cleanupFailure)
                        .given(verificationRepository)
                        .deleteChallengeAndCooldownIfMatches(
                                eq(VerificationPurpose.SIGN_UP),
                                eq(EMAIL),
                                any()
                        );

                // When
                Throwable thrown = catchThrowable(() -> service.requestCode(
                        EMAIL,
                        VerificationPurpose.SIGN_UP
                ));

                // Then
                then(thrown).isSameAs(cleanupFailure);
                then(cleanupFailure.getSuppressed()).containsExactly(dispatchFailure);
            }
        }
    }

    @Nested
    @DisplayName("인증 코드 검증")
    class VerifyAndConsumeCodeTest {

        @Test
        @DisplayName("올바르면 원자적으로 소비한다")
        void verifiesAndConsumesCode() {
            // Given
            given(verificationRepository.verifyAndConsume(
                    VerificationPurpose.SIGN_UP,
                    EMAIL,
                    CHALLENGE_ID,
                    CODE,
                    5
            )).willReturn(OtpVerificationStatus.VERIFIED);

            // When
            service.verifyAndConsumeCode(
                    EMAIL,
                    VerificationPurpose.SIGN_UP,
                    CHALLENGE_ID,
                    CODE
            );

            // Then
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
        @DisplayName("잘못됐거나 실패 제한을 초과하면 일반 인증 오류를 반환한다")
        void rejectsInvalidChallenge(OtpVerificationStatus status) {
            // Given
            given(verificationRepository.verifyAndConsume(
                    VerificationPurpose.PASSWORD_CHANGE,
                    EMAIL,
                    CHALLENGE_ID,
                    "999999",
                    5
            )).willReturn(status);

            // When
            Throwable thrown = catchThrowable(() -> service.verifyAndConsumeCode(
                    EMAIL,
                    VerificationPurpose.PASSWORD_CHANGE,
                    CHALLENGE_ID,
                    "999999"
            ));

            // Then
            then(thrown).isInstanceOfSatisfying(
                    BusinessException.class,
                    exception -> then(exception.getErrorCode())
                            .isSameAs(EmailVerificationErrorCode.INVALID)
            );
        }

        @Test
        @DisplayName("저장소 장애를 원인과 함께 가용성 오류로 변환한다")
        void translatesStorageFailureDuringVerification() {
            // Given
            IllegalStateException redisFailure = new IllegalStateException("Redis 연결 실패");
            EmailVerificationStorageException storageFailure =
                    new EmailVerificationStorageException(redisFailure);
            given(verificationRepository.verifyAndConsume(
                    VerificationPurpose.PASSWORD_CHANGE,
                    EMAIL,
                    CHALLENGE_ID,
                    CODE,
                    5
            )).willThrow(storageFailure);

            // When
            Throwable thrown = catchThrowable(() -> service.verifyAndConsumeCode(
                    EMAIL,
                    VerificationPurpose.PASSWORD_CHANGE,
                    CHALLENGE_ID,
                    CODE
            ));

            // Then
            thenUnavailableWithOriginalCause(thrown, storageFailure, redisFailure);
        }
    }

    private void thenUnavailableWithOriginalCause(
            Throwable thrown,
            EmailVerificationStorageException storageFailure,
            Throwable redisFailure
    ) {
        then(thrown).isInstanceOfSatisfying(BusinessException.class, exception -> {
            then(exception.getErrorCode())
                    .isSameAs(EmailVerificationErrorCode.UNAVAILABLE);
            then(exception.getCause()).isSameAs(storageFailure);
            then(exception.getCause().getCause()).isSameAs(redisFailure);
        });
    }

    private void givenReservation(EmailVerificationReservationResult result) {
        given(verificationRepository.reserveChallenge(
                any(VerificationPurpose.class),
                any(String.class),
                any(OtpChallenge.class),
                any(Duration.class),
                any(Duration.class)
        )).willReturn(result);
    }
}
