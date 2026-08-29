package site.omagotchi.identityservice.integration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class TestcontainersConfig {

	private static final String REDIS_PASSWORD =
			"test-only-authentication-epoch-password";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18.1"));
	}

	@Bean
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
				.withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
				.withExposedPorts(6379);
	}

	@Bean
	DynamicPropertyRegistrar authenticationEpochRedisProperties(
			@Qualifier("redisContainer") GenericContainer<?> redisContainer
	) {
		return registry -> {
			registry.add("auth.authentication-epoch.redis.host", redisContainer::getHost);
			registry.add(
					"auth.authentication-epoch.redis.port",
					redisContainer::getFirstMappedPort
			);
		};
	}

}
