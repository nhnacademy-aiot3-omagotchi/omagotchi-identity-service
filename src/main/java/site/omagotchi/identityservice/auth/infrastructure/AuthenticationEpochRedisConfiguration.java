package site.omagotchi.identityservice.auth.infrastructure;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthenticationEpochRedisProperties.class)
public class AuthenticationEpochRedisConfiguration {

    @Bean
    RedisConnectionFactory authenticationEpochRedisConnectionFactory(
            AuthenticationEpochRedisProperties properties
    ) {
        RedisStandaloneConfiguration serverConfiguration =
                new RedisStandaloneConfiguration(properties.host(), properties.port());
        serverConfiguration.setDatabase(properties.database());
        serverConfiguration.setUsername(properties.username());
        serverConfiguration.setPassword(RedisPassword.of(properties.password()));

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(properties.connectTimeout())
                        .build())
                .timeoutOptions(TimeoutOptions.enabled())
                .build();
        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfiguration =
                LettuceClientConfiguration.builder()
                        .commandTimeout(properties.commandTimeout())
                        .clientOptions(clientOptions);
        if (properties.sslEnabled()) {
            clientConfiguration.useSsl();
        }

        return new LettuceConnectionFactory(
                serverConfiguration,
                clientConfiguration.build()
        );
    }

    @Bean
    StringRedisTemplate authenticationEpochRedisTemplate(
            @Qualifier("authenticationEpochRedisConnectionFactory")
            RedisConnectionFactory connectionFactory
    ) {
        return new StringRedisTemplate(connectionFactory);
    }
}
