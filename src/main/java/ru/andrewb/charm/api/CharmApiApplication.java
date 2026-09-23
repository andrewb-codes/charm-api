package ru.andrewb.charm.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.andrewb.charm.api.security.access.JwtProperties;
import ru.andrewb.charm.api.security.refresh.RefreshTokenProperties;
import ru.andrewb.charm.api.storage.StorageProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        RefreshTokenProperties.class,
        StorageProperties.class
})
public class CharmApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CharmApiApplication.class, args);
    }
}
