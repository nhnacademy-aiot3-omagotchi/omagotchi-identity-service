package site.omagotchi.identityservice.emailverification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.identityservice.emailverification.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationChallenge;
import site.omagotchi.identityservice.emailverification.domain.EmailVerificationPurpose;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationUseService {

    private final EmailVerificationRepository repository;
    private final VerificationCodeAuthenticator codeAuthenticator;
    private final EmailVerificationProperties properties;
    private final Clock clock;

    /** 회원가입용 OTP가 요청 문맥과 일치하는지 검증한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean verifySignupOtp(
            UUID challengeId,
            String normalizedEmail,
            String code
    ) {
        return verify(
                challengeId,
                normalizedEmail,
                EmailVerificationPurpose.SIGNUP,
                code
        );
    }

    /** 비밀번호 변경용 OTP가 요청 문맥과 일치하는지 검증한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean verifyPasswordChangeOtp(
            UUID challengeId,
            String normalizedEmail,
            String code
    ) {
        return verify(
                challengeId,
                normalizedEmail,
                EmailVerificationPurpose.PASSWORD_CHANGE,
                code
        );
    }

    /** 비밀번호 재설정용 OTP가 요청 문맥과 일치하는지 검증한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean verifyPasswordResetOtp(
            UUID challengeId,
            String normalizedEmail,
            String code
    ) {
        return verify(
                challengeId,
                normalizedEmail,
                EmailVerificationPurpose.PASSWORD_RESET,
                code
        );
    }

    /** Challenge를 잠그고 이메일·목적·코드·유효성을 검증한다. */
    private boolean verify(
            UUID challengeId,
            String normalizedEmail,
            EmailVerificationPurpose purpose,
            String code
    ) {
        EmailVerificationChallenge challenge = repository.lockChallenge(challengeId)
                .orElse(null);
        Instant checkedAt = currentTime();

        if (challenge == null
                || !challenge.matchesContext(normalizedEmail, purpose)
                || !challenge.isUsableAt(checkedAt)) {
            return false;
        }

        boolean validFormat = code != null && code.matches("\\d{6}");
        boolean matched = validFormat && codeAuthenticator.matches(
                challenge.getCodeMac(),
                challengeId,
                normalizedEmail,
                purpose,
                code
        );
        if (!matched) {
            challenge.recordInvalidAttempt(properties.maximumFailedAttempts(), checkedAt);
            return false;
        }
        return true;
    }

    /** 검증에 성공한 Challenge를 현재 트랜잭션에서 소비한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(UUID challengeId) {
        EmailVerificationChallenge challenge = repository.lockChallenge(challengeId)
                .orElseThrow(() -> new IllegalStateException("검증한 이메일 인증을 찾을 수 없습니다."));
        challenge.consume(currentTime());
    }

    /** 영속성 정밀도에 맞춰 현재 시각을 마이크로초 단위로 반환한다. */
    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
