package ru.andrewb.charm.api.security.refresh;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.refresh-token")
public record RefreshTokenProperties(
    Duration ttl
) {
}
