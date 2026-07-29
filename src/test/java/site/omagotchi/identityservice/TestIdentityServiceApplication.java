package site.omagotchi.identityservice;

import org.springframework.boot.SpringApplication;

public class TestIdentityServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(IdentityServiceApplication::main).with(TestcontainersConfig.class).run(args);
	}

}
