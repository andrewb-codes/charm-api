package ru.andrewb.charm.api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        Limit login,
        Limit registration,
        Limit refresh
) {
    public RateLimitProperties {
        if (login == null || registration == null || refresh == null) {
            throw new IllegalArgumentException("All rate limits must be configured");
        }
    }

    public record Limit(long capacity, Duration refillPeriod) {
        public Limit {
            if (capacity <= 0 || refillPeriod == null
                    || refillPeriod.isZero() || refillPeriod.isNegative()) {
                throw new IllegalArgumentException(
                        "Rate limit capacity and refill period must be positive"
                );
            }
        }
    }
}
