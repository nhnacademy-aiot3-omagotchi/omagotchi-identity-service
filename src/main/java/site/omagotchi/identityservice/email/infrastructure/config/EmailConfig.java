package site.omagotchi.identityservice.email.infrastructure.config;

import com.resend.Resend;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration(proxyBeanMethods = false)
public class EmailConfig {

    // Resend HTTP API 메일 발송 클라이언트
    @Bean
    Resend resendClient(ResendProperties properties) {
        return new Resend(properties.apiKey());
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
