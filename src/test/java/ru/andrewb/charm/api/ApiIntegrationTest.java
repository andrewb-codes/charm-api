package ru.andrewb.charm.api;

import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import ru.andrewb.charm.api.storage.FileStorage;
import ru.andrewb.charm.api.user.persistence.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @MockitoBean
    protected FileStorage fileStorage;

    @Autowired
    @Qualifier("rateLimitRedisConnection")
    private StatefulRedisConnection<String, byte[]> rateLimitRedisConnection;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
        // Only rate-limit keys in the isolated Testcontainers Redis are removed.
        var keys = rateLimitRedisConnection.sync().keys("charm:rate-limit:*");
        if (!keys.isEmpty()) {
            rateLimitRedisConnection.sync().del(keys.toArray(String[]::new));
        }
    }

    @ServiceConnection(name = "redis")
    protected static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379);

    static {
        redis.start();
    }
}
