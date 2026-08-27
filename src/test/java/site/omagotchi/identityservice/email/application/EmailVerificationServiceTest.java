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
import site.omagotchi.identityservice.account.application.AccountErrorCode;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.account.domain.AccountStatus;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.email.domain.VerifiedEmail;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDSoftAssertions.thenSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String INPUT_EMAIL = "  USER@Example.COM  ";
    private static final String EMAIL = "user@example.com";
    private static final String CHALLENGE_ID = "challenge-id";
    private static final String CODE = "042910";
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration COOLDOWN = Duration.ofMinutes(1);

    @Mock
    private AccountRepository accountRepository;

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
                accountRepository,
                verificationRepository,
                codeGenerator,
                mailDispatchService,
                new EmailVerificationProperties(CODE_TTL, TOKEN_TTL, COOLDOWN, 5)
        );
    }

    @Test
    @DisplayName("가입되지 않은 이메일은 새 OTP Challenge를 저장하고 발송")
    void requestsSignUpChallengeForNewEmail() {
        given(verificationRepository.acquireCooldown(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                COOLDOWN
        )).willReturn(true);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
        given(codeGenerator.generate()).willReturn(CODE);
        ArgumentCaptor<OtpChallenge> challengeCaptor =
                ArgumentCaptor.forClass(OtpChallenge.class);

        EmailVerificationChallengeResult result = service.requestCode(
                INPUT_EMAIL,
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
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
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

        verify(verificationRepository, times(2))
                .replaceChallenge(
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
            softly.then(challengeCaptor.getAllValues())
                    .extracting(OtpChallenge::challengeId)
                    .containsExactly(first.challengeId(), second.challengeId());
        });
    }

    @Test
    @DisplayName("이미 가입된 이메일은 동일한 Challenge 응답만 반환하고 메일 발송 생략")
    void suppressesSignUpChallengeForExistingEmail() {
        Account account = mock(Account.class);
        given(verificationRepository.acquireCooldown(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                COOLDOWN
        )).willReturn(true);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));

        EmailVerificationChallengeResult result = service.requestCode(
                EMAIL,
                VerificationPurpose.SIGN_UP
        );

        then(result.challengeId()).matches("[0-9a-f-]{36}");
        verifyNoInteractions(codeGenerator, mailDispatchService);
        verify(verificationRepository, never()).replaceChallenge(
                any(), any(), any(), any()
        );
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "LOCKED"})
    @DisplayName("활성 또는 잠긴 계정은 비밀번호 재설정 Challenge 발송")
    void requestsPasswordResetChallengeForRecoverableAccount(AccountStatus status) {
        Account account = mock(Account.class);
        given(account.getStatus()).willReturn(status);
        given(verificationRepository.acquireCooldown(
                VerificationPurpose.PASSWORD_RESET,
                EMAIL,
                COOLDOWN
        )).willReturn(true);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));
        given(codeGenerator.generate()).willReturn(CODE);

        EmailVerificationChallengeResult result = service.requestCode(
                EMAIL,
                VerificationPurpose.PASSWORD_RESET
        );

        verify(mailDispatchService).dispatch(
                VerificationPurpose.PASSWORD_RESET,
                EMAIL,
                CODE,
                result.challengeId(),
                CODE_TTL
        );
    }

    @Test
    @DisplayName("활성 계정은 비밀번호 변경 Challenge 발송")
    void requestsPasswordChangeChallengeForActiveAccount() {
        Account account = mock(Account.class);
        given(account.getStatus()).willReturn(AccountStatus.ACTIVE);
        given(verificationRepository.acquireCooldown(
                VerificationPurpose.PASSWORD_CHANGE,
                EMAIL,
                COOLDOWN
        )).willReturn(true);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));
        given(codeGenerator.generate()).willReturn(CODE);

        EmailVerificationChallengeResult result = service.requestCode(
                EMAIL,
                VerificationPurpose.PASSWORD_CHANGE
        );

        verify(mailDispatchService).dispatch(
                VerificationPurpose.PASSWORD_CHANGE,
                EMAIL,
                CODE,
                result.challengeId(),
                CODE_TTL
        );
    }

    @Test
    @DisplayName("재발송 대기 중이면 남은 시간과 함께 요청 제한 오류")
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
                exception -> thenSoftly(softly -> {
                    softly.then(exception.getErrorCode())
                            .isSameAs(EmailVerificationErrorCode.COOLDOWN_ACTIVE);
                    softly.then(exception.retryAfterSeconds()).isEqualTo(42L);
                })
        );
        verifyNoInteractions(accountRepository, codeGenerator, mailDispatchService);
    }

    @Test
    @DisplayName("올바른 Challenge와 OTP를 원자적으로 소비하고 인증 Token으로 교환")
    void exchangesVerifiedChallengeForVerificationToken() {
        given(verificationRepository.verifyAndConsume(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                CHALLENGE_ID,
                CODE,
                5
        )).willReturn(OtpVerificationStatus.VERIFIED);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        EmailVerificationResult result = service.verifyCode(
                INPUT_EMAIL,
                VerificationPurpose.SIGN_UP,
                CHALLENGE_ID,
                CODE
        );

        verify(verificationRepository).saveVerifiedToken(
                tokenCaptor.capture(),
                eq(new VerifiedEmail(EMAIL, VerificationPurpose.SIGN_UP)),
                eq(TOKEN_TTL)
        );
        thenSoftly(softly -> {
            softly.then(result.verificationToken()).isEqualTo(tokenCaptor.getValue());
            softly.then(result.verificationToken()).matches("[0-9a-f-]{36}");
            softly.then(result.expiresInSeconds()).isEqualTo(900L);
        });
    }

    @ParameterizedTest
    @EnumSource(value = OtpVerificationStatus.class, names = {"INVALID", "EXHAUSTED"})
    @DisplayName("잘못되거나 실패 제한을 초과한 Challenge는 동일한 일반 인증 오류 반환")
    void rejectsInvalidChallenge(OtpVerificationStatus status) {
        given(verificationRepository.verifyAndConsume(
                VerificationPurpose.PASSWORD_RESET,
                EMAIL,
                CHALLENGE_ID,
                "999999",
                5
        )).willReturn(status);

        Throwable thrown = catchThrowable(() -> service.verifyCode(
                EMAIL,
                VerificationPurpose.PASSWORD_RESET,
                CHALLENGE_ID,
                "999999"
        ));

        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isSameAs(EmailVerificationErrorCode.INVALID)
        );
        verify(verificationRepository, never()).saveVerifiedToken(any(), any(), any());
    }

    @Test
    @DisplayName("비동기 작업 접수 실패 시 해당 Challenge만 삭제")
    void deletesOnlyCurrentChallengeWhenDispatchIsRejected() {
        given(verificationRepository.acquireCooldown(
                VerificationPurpose.SIGN_UP,
                EMAIL,
                COOLDOWN
        )).willReturn(true);
        given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
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
    @DisplayName("유효하지 않은 이메일은 Redis 접근 전에 기존 이메일 오류로 거절")
    void rejectsInvalidEmailBeforeRepositoryAccess() {
        Throwable thrown = catchThrowable(() -> service.requestCode(
                "not-an-email",
                VerificationPurpose.SIGN_UP
        ));

        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isSameAs(AccountErrorCode.INVALID_EMAIL)
        );
        verifyNoInteractions(
                accountRepository,
                verificationRepository,
                codeGenerator,
                mailDispatchService
        );
    }
}
