package site.omagotchi.identityservice.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestJwtConfig.class, DatabaseCleaner.class})
public abstract class BaseIntegrationTest {

    @Autowired
    protected DatabaseCleaner databaseCleaner;

    protected void cleanDatabase() {
        databaseCleaner.clean();
    }
}
