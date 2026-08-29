package site.omagotchi.identityservice.email.infrastructure.config;

import com.resend.Resend;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import site.omagotchi.identityservice.email.application.port.EmailDeliveryException;
import site.omagotchi.identityservice.email.application.port.VerificationMailSender;
import site.omagotchi.identityservice.email.infrastructure.ResendEmailSender;
import site.omagotchi.identityservice.email.infrastructure.RetryingVerificationMailSender;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.Executor;

@EnableAsync
@Configuration(proxyBeanMethods = false)
public class EmailConfig {

    private static final int MAX_MAIL_RETRIES = 2;
    private static final Duration MAIL_RETRY_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MAIL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration MAIL_RETRY_JITTER = Duration.ofMillis(250);
    private static final double MAIL_RETRY_MULTIPLIER = 2.0;
    private static final Duration MAX_MAIL_RETRY_DELAY = Duration.ofSeconds(2);

    // Resend HTTP API 메일 발송 클라이언트
    @Bean
    Resend resendClient(ResendProperties properties) {
        return new Resend(properties.apiKey());
    }

    @Bean
    SecureRandom verificationCodeSecureRandom() {
        return new SecureRandom();
    }

    @Bean("verificationMailRetryTemplate")
    RetryTemplate verificationMailRetryTemplate() {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .predicate(throwable -> throwable instanceof EmailDeliveryException exception
                        && exception.retryable())
                .maxRetries(MAX_MAIL_RETRIES)
                .timeout(MAIL_RETRY_TIMEOUT)
                .delay(MAIL_RETRY_DELAY)
                .jitter(MAIL_RETRY_JITTER)
                .multiplier(MAIL_RETRY_MULTIPLIER)
                .maxDelay(MAX_MAIL_RETRY_DELAY)
                .build();
        return new RetryTemplate(retryPolicy);
    }

    @Bean
    @Primary
    VerificationMailSender verificationMailSender(
            ResendEmailSender resendEmailSender,
            RetryTemplate verificationMailRetryTemplate
    ) {
        return new RetryingVerificationMailSender(resendEmailSender, verificationMailRetryTemplate);
    }

    // 이메일 인증 코드·재발송 쿨다운·인증 완료 토큰 수명 관리용 RedisTemplate
    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    // 메일 발송 I/O 격리를 위한 전용 비동기 스레드 풀
    // TODO: 현재 executor의 값은 초기 운영값이며 아키텍처 계약이 아님 추후에 환경에 따른 변경 필요함
    @Bean("mailTaskExecutor")
    Executor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("mail-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
