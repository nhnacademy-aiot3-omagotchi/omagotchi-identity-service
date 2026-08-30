package site.omagotchi.identityservice.email.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import site.omagotchi.identityservice.email.application.port.EmailVerificationRepository;
import site.omagotchi.identityservice.email.application.port.VerificationMailSender;
import site.omagotchi.identityservice.email.domain.OtpChallenge;
import site.omagotchi.identityservice.email.domain.OtpVerificationStatus;
import site.omagotchi.identityservice.email.domain.VerificationPurpose;
import site.omagotchi.identityservice.global.exception.BusinessException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.BDDAssertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;

@SpringJUnitConfig(EmailVerificationExecutorRejectionTest.TestConfig.class)
@DisplayName("이메일 인증 비동기 Executor 거부")
class EmailVerificationExecutorRejectionTest {

    private static final String EMAIL = "user@example.com";

    @Autowired
    private EmailVerificationService service;

    @Autowired
    private StatefulEmailVerificationRepository repository;

    @Autowired
    private RecordingVerificationMailSender mailSender;

    @Autowired
    @Qualifier("mailTaskExecutor")
    private ThreadPoolTaskExecutor mailTaskExecutor;

    @Test
    @DisplayName("포화된 실제 Executor는 자신이 만든 Challenge와 쿨다운을 제거")
    void removesOwnedReservationWhenExecutorIsSaturated()
            throws InterruptedException {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        mailTaskExecutor.execute(() -> {
            taskStarted.countDown();
            awaitRelease(releaseTask);
        });

        try {
            then(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Throwable thrown = catchThrowable(() -> service.requestCode(
                    EMAIL,
                    VerificationPurpose.SIGN_UP
            ));

            then(thrown).isInstanceOfSatisfying(BusinessException.class, exception -> {
                then(exception.getErrorCode())
                        .isSameAs(EmailVerificationErrorCode.UNAVAILABLE);
                then(exception.getCause()).isInstanceOf(TaskRejectedException.class);
            });
            then(repository.currentChallenge()).isNull();
            then(repository.currentCooldownOwner()).isNull();
            then(mailSender.wasInvoked()).isFalse();
        } finally {
            releaseTask.countDown();
        }
    }

    private static void awaitRelease(CountDownLatch releaseTask) {
        try {
            releaseTask.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class TestConfig {

        @Bean("mailTaskExecutor")
        ThreadPoolTaskExecutor mailTaskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(0);
            executor.setThreadNamePrefix("mail-rejection-test-");
            return executor;
        }

        @Bean
        StatefulEmailVerificationRepository emailVerificationRepository() {
            return new StatefulEmailVerificationRepository();
        }

        @Bean
        RecordingVerificationMailSender verificationMailSender() {
            return new RecordingVerificationMailSender();
        }

        @Bean
        VerificationCodeGenerator verificationCodeGenerator() {
            return new VerificationCodeGenerator(new SecureRandom());
        }

        @Bean
        EmailVerificationProperties emailVerificationProperties() {
            return new EmailVerificationProperties(
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(1),
                    5
            );
        }

        @Bean
        VerificationMailDispatchService verificationMailDispatchService(
                VerificationMailSender mailSender,
                EmailVerificationRepository repository
        ) {
            return new VerificationMailDispatchService(mailSender, repository);
        }

        @Bean
        EmailVerificationService emailVerificationService(
                EmailVerificationRepository repository,
                VerificationCodeGenerator codeGenerator,
                VerificationMailDispatchService mailDispatchService,
                EmailVerificationProperties properties
        ) {
            return new EmailVerificationService(
                    repository,
                    codeGenerator,
                    mailDispatchService,
                    properties
            );
        }
    }

    static final class StatefulEmailVerificationRepository
            implements EmailVerificationRepository {

        private final AtomicReference<OtpChallenge> challenge = new AtomicReference<>();
        private final AtomicReference<String> cooldownOwner = new AtomicReference<>();

        @Override
        public EmailVerificationReservationResult reserveChallenge(
                VerificationPurpose purpose,
                String email,
                OtpChallenge challenge,
                Duration challengeTtl,
                Duration cooldownTtl
        ) {
            if (!cooldownOwner.compareAndSet(null, challenge.challengeId())) {
                return EmailVerificationReservationResult.cooldown(60L);
            }
            this.challenge.set(challenge);
            return EmailVerificationReservationResult.acquired();
        }

        @Override
        public OtpVerificationStatus verifyAndConsume(
                VerificationPurpose purpose,
                String email,
                String challengeId,
                String verificationCode,
                int maximumAttempts
        ) {
            return OtpVerificationStatus.INVALID;
        }

        @Override
        public boolean deleteChallengeIfMatches(
                VerificationPurpose purpose,
                String email,
                String challengeId
        ) {
            OtpChallenge current;
            do {
                current = challenge.get();
                if (current == null
                        || !current.challengeId().equals(challengeId)) {
                    return false;
                }
            } while (!challenge.compareAndSet(current, null));
            return true;
        }

        @Override
        public void deleteChallengeAndCooldownIfMatches(
                VerificationPurpose purpose,
                String email,
                String challengeId
        ) {
            deleteChallengeIfMatches(purpose, email, challengeId);
            cooldownOwner.compareAndSet(challengeId, null);
        }

        OtpChallenge currentChallenge() {
            return challenge.get();
        }

        String currentCooldownOwner() {
            return cooldownOwner.get();
        }
    }

    static final class RecordingVerificationMailSender
            implements VerificationMailSender {

        private final AtomicBoolean invoked = new AtomicBoolean();

        @Override
        public void sendVerificationCode(
                String recipient,
                String verificationCode,
                String challengeId,
                Duration validity
        ) {
            invoked.set(true);
        }

        boolean wasInvoked() {
            return invoked.get();
        }
    }
}
