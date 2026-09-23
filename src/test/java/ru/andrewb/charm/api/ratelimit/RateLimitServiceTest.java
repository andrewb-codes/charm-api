package ru.andrewb.charm.api.ratelimit;

import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.andrewb.charm.api.ApiIntegrationTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest extends ApiIntegrationTest {

    @Autowired
    private ProxyManager<String> rateLimitProxyManager;

    @Autowired
    @Qualifier("rateLimitRedisConnection")
    private StatefulRedisConnection<String, byte[]> connection;

    @Test
    void sharesStateBetweenServiceInstancesAndSeparatesIps() {
        var properties = properties(2, Duration.ofHours(1));
        var first = new RateLimitService(rateLimitProxyManager, properties);
        var second = new RateLimitService(rateLimitProxyManager, properties);

        assertThat(first.tryConsume(RateLimitOperation.LOGIN, "192.0.2.1").isConsumed()).isTrue();
        assertThat(second.tryConsume(RateLimitOperation.LOGIN, "192.0.2.1").isConsumed()).isTrue();
        assertThat(first.tryConsume(RateLimitOperation.LOGIN, "192.0.2.1").isConsumed()).isFalse();
        assertThat(second.tryConsume(RateLimitOperation.LOGIN, "192.0.2.2").isConsumed()).isTrue();

        // All three operations have their own Redis key for the same IP.
        assertThat(first.tryConsume(RateLimitOperation.REGISTRATION, "192.0.2.1").isConsumed()).isTrue();
        assertThat(first.tryConsume(RateLimitOperation.REFRESH, "192.0.2.1").isConsumed()).isTrue();
        assertThat(second.tryConsume(RateLimitOperation.REGISTRATION, "192.0.2.1").isConsumed()).isTrue();
        assertThat(second.tryConsume(RateLimitOperation.REGISTRATION, "192.0.2.1").isConsumed()).isFalse();
        assertThat(second.tryConsume(RateLimitOperation.REFRESH, "192.0.2.1").isConsumed()).isTrue();
        assertThat(second.tryConsume(RateLimitOperation.REFRESH, "192.0.2.1").isConsumed()).isFalse();

        for (RateLimitOperation operation : RateLimitOperation.values()) {
            long ttl = connection.sync().pttl(
                    "charm:rate-limit:" + operation.keyPart() + ":192.0.2.1");
            assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(61).toMillis());
        }
    }

    @Test
    void appliesTheConfiguredCapacityForEachOperation() {
        var properties = new RateLimitProperties(
                new RateLimitProperties.Limit(1, Duration.ofHours(1)),
                new RateLimitProperties.Limit(2, Duration.ofHours(1)),
                new RateLimitProperties.Limit(3, Duration.ofHours(1))
        );
        var service = new RateLimitService(rateLimitProxyManager, properties);
        String ip = "192.0.2.5";

        assertThat(service.tryConsume(RateLimitOperation.LOGIN, ip).isConsumed()).isTrue();
        assertThat(service.tryConsume(RateLimitOperation.LOGIN, ip).isConsumed()).isFalse();

        for (int i = 0; i < 2; i++) {
            assertThat(service.tryConsume(RateLimitOperation.REGISTRATION, ip).isConsumed())
                    .isTrue();
        }
        assertThat(service.tryConsume(RateLimitOperation.REGISTRATION, ip).isConsumed())
                .isFalse();

        for (int i = 0; i < 3; i++) {
            assertThat(service.tryConsume(RateLimitOperation.REFRESH, ip).isConsumed())
                    .isTrue();
        }
        assertThat(service.tryConsume(RateLimitOperation.REFRESH, ip).isConsumed())
                .isFalse();
    }

    @Test
    void refillsGraduallyWithoutSleeping() {
        var now = new AtomicLong(TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()));
        TimeMeter clock = new TimeMeter() {
            public long currentTimeNanos() { return now.get(); }
            public boolean isWallClockBased() { return true; }
        };
        var manager = Bucket4jLettuce.casBasedBuilder(connection).clientClock(clock).build();
        var service = new RateLimitService(manager, properties(2, Duration.ofSeconds(10)));

        assertThat(service.tryConsume(RateLimitOperation.REFRESH, "192.0.2.3").isConsumed()).isTrue();
        assertThat(service.tryConsume(RateLimitOperation.REFRESH, "192.0.2.3").isConsumed()).isTrue();
        assertThat(service.tryConsume(RateLimitOperation.REFRESH, "192.0.2.3").getNanosToWaitForRefill())
                .isEqualTo(Duration.ofSeconds(5).toNanos());
        now.addAndGet(Duration.ofSeconds(4).toNanos());
        assertThat(service.tryConsume(RateLimitOperation.REFRESH, "192.0.2.3").isConsumed()).isFalse();
        now.addAndGet(Duration.ofSeconds(1).toNanos());
        assertThat(service.tryConsume(RateLimitOperation.REFRESH, "192.0.2.3").isConsumed()).isTrue();
        assertThat(service.tryConsume(RateLimitOperation.REFRESH, "192.0.2.3").isConsumed()).isFalse();
    }

    @Test
    void concurrentRequestsCannotExceedSharedCapacity() throws Exception {
        var properties = properties(5, Duration.ofHours(1));
        var first = new RateLimitService(rateLimitProxyManager, properties);
        var second = new RateLimitService(rateLimitProxyManager, properties);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<Boolean>>();

        try (var executor = Executors.newFixedThreadPool(12)) {
            for (int i = 0; i < 12; i++) {
                var service = i % 2 == 0 ? first : second;
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.tryConsume(RateLimitOperation.REGISTRATION, "192.0.2.4")
                            .isConsumed();
                }));
            }
            start.countDown();
            int accepted = 0;
            for (var future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) accepted++;
            }
            assertThat(accepted).isEqualTo(5);
        }
    }

    private RateLimitProperties properties(long capacity, Duration period) {
        var limit = new RateLimitProperties.Limit(capacity, period);
        return new RateLimitProperties(limit, limit, limit);
    }
}
