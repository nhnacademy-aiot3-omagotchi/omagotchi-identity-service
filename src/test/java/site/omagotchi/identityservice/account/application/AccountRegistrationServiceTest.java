package site.omagotchi.identityservice.account.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import site.omagotchi.identityservice.account.application.port.AccountRepository;
import site.omagotchi.identityservice.account.application.port.PasswordHasher;
import site.omagotchi.identityservice.account.domain.Account;
import site.omagotchi.identityservice.email.application.EmailVerificationChallengeResult;
import site.omagotchi.identityservice.email.application.EmailVerificationService;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.util.Optional;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AccountRegistrationServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password-passphrase";
    private static final String NAME = "사용자";
    private static final String CHALLENGE_ID = "challenge-id";
    private static final String CODE = "123456";

    @Test
    @DisplayName("가입 입력과 이메일 중복 확인 후 SIGN_UP OTP 발급")
    void requestsSignUpEmailOtpAfterPrecheck() {
        Fixture fixture = fixture();
        EmailVerificationChallengeResult expected =
                new EmailVerificationChallengeResult(CHALLENGE_ID, 600L);
        given(fixture.accountRepository().findByEmail(EMAIL))
                .willReturn(Optional.empty());
        given(fixture.emailVerificationService().requestCode(
                EMAIL,
                VerificationPurpose.SIGN_UP
        )).willReturn(expected);

        EmailVerificationChallengeResult result = fixture.service()
                .requestEmailOtp("  USER@Example.com  ", PASSWORD, NAME);

        then(result).isSameAs(expected);
        verify(fixture.accountRepository()).findByEmail(EMAIL);
        verify(fixture.emailVerificationService()).requestCode(
                EMAIL,
                VerificationPurpose.SIGN_UP
        );
        verifyNoInteractions(fixture.passwordHasher());
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 OTP를 발급하지 않음")
    void rejectsDuplicateEmailBeforeIssuingCode() {
        Fixture fixture = fixture();
        given(fixture.accountRepository().findByEmail(EMAIL))
                .willReturn(Optional.of(Account.register(
                        EMAIL,
                        "encoded-password",
                        NAME
                )));

        Throwable thrown = catchThrowable(() -> fixture.service()
                .requestEmailOtp(EMAIL, PASSWORD, NAME));

        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isSameAs(AccountErrorCode.DUPLICATE_EMAIL)
        );
        verifyNoInteractions(
                fixture.passwordHasher(),
                fixture.emailVerificationService()
        );
    }

    @Test
    @DisplayName("비밀번호 최대 UTF-8 바이트 위반을 비밀번호 오류로 변환")
    void rejectsPasswordOverMaximumUtf8Bytes() {
        Fixture fixture = fixture();
        String password = "가".repeat(24) + "a1";

        Throwable thrown = catchThrowable(() -> fixture.service().signUp(
                EMAIL,
                password,
                NAME,
                CHALLENGE_ID,
                CODE
        ));

        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.INVALID_PASSWORD)
        );
        verifyNoInteractions(
                fixture.accountRepository(),
                fixture.passwordHasher(),
                fixture.emailVerificationService()
        );
    }

    @Test
    @DisplayName("HTTP 외부에서도 잘못된 이메일을 이메일 오류로 변환")
    void rejectsInvalidEmailOutsidePresentation() {
        Fixture fixture = fixture();

        Throwable thrown = catchThrowable(() -> fixture.service().signUp(
                "not-an-email",
                PASSWORD,
                NAME,
                CHALLENGE_ID,
                CODE
        ));

        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.INVALID_EMAIL)
        );
        verifyNoInteractions(
                fixture.accountRepository(),
                fixture.passwordHasher(),
                fixture.emailVerificationService()
        );
    }

    @Test
    @DisplayName("최대 길이를 넘은 이름을 이름 오류로 변환")
    void rejectsNameOverMaximumLength() {
        Fixture fixture = fixture();

        Throwable thrown = catchThrowable(() -> fixture.service().signUp(
                EMAIL,
                PASSWORD,
                "가".repeat(31),
                CHALLENGE_ID,
                CODE
        ));

        then(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> then(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.INVALID_NAME)
        );
        verifyNoInteractions(
                fixture.accountRepository(),
                fixture.passwordHasher(),
                fixture.emailVerificationService()
        );
    }

    @Test
    @DisplayName("저장소가 분류한 업무 실패는 다시 감싸지 않음")
    void preservesRepositoryBusinessFailure() {
        Fixture fixture = fixture();
        IllegalStateException persistenceFailure =
                new IllegalStateException("persistence failure");
        BusinessException repositoryFailure = new BusinessException(
                AccountErrorCode.DUPLICATE_EMAIL,
                persistenceFailure
        );
        given(fixture.passwordHasher().hash(PASSWORD))
                .willReturn("encoded-password");
        given(fixture.accountRepository().create(any(Account.class)))
                .willThrow(repositoryFailure);

        Throwable thrown = catchThrowable(() -> fixture.service().signUp(
                EMAIL,
                PASSWORD,
                NAME,
                CHALLENGE_ID,
                CODE
        ));

        then(thrown).isSameAs(repositoryFailure).hasCause(persistenceFailure);
        verifyNoInteractions(fixture.emailVerificationService());
    }

    @Test
    @DisplayName("예상하지 못한 저장 실패는 원본 그대로 전파")
    void preservesUnexpectedRepositoryFailure() {
        Fixture fixture = fixture();
        RuntimeException repositoryFailure =
                new RuntimeException("database unavailable");
        given(fixture.passwordHasher().hash(PASSWORD))
                .willReturn("encoded-password");
        given(fixture.accountRepository().create(any(Account.class)))
                .willThrow(repositoryFailure);

        Throwable thrown = catchThrowable(() -> fixture.service().signUp(
                EMAIL,
                PASSWORD,
                NAME,
                CHALLENGE_ID,
                CODE
        ));

        then(thrown).isSameAs(repositoryFailure);
        verifyNoInteractions(fixture.emailVerificationService());
    }

    @Test
    @DisplayName("계정 저장 후 SIGN_UP OTP를 검증하고 소비")
    void verifiesAndConsumesSignUpCodeAfterCreatingAccount() {
        Fixture fixture = fixture();
        Account createdAccount = Account.register(
                EMAIL,
                "encoded-password",
                NAME
        );
        given(fixture.passwordHasher().hash(PASSWORD))
                .willReturn("encoded-password");
        given(fixture.accountRepository().create(any(Account.class)))
                .willReturn(createdAccount);

        Account result = fixture.service().signUp(
                "  USER@example.com  ",
                PASSWORD,
                NAME,
                CHALLENGE_ID,
                CODE
        );

        then(result).isSameAs(createdAccount);
        InOrder order = inOrder(
                fixture.accountRepository(),
                fixture.emailVerificationService()
        );
        order.verify(fixture.accountRepository()).create(any(Account.class));
        order.verify(fixture.emailVerificationService()).verifyAndConsumeCode(
                EMAIL,
                VerificationPurpose.SIGN_UP,
                CHALLENGE_ID,
                CODE
        );
    }

    private Fixture fixture() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        EmailVerificationService emailVerificationService = mock(
                EmailVerificationService.class
        );
        return new Fixture(
                accountRepository,
                passwordHasher,
                emailVerificationService,
                new AccountRegistrationService(
                        accountRepository,
                        passwordHasher,
                        emailVerificationService
                )
        );
    }

    private record Fixture(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            EmailVerificationService emailVerificationService,
            AccountRegistrationService service
    ) {
    }
}
