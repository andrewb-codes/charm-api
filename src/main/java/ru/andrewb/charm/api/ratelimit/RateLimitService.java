package ru.andrewb.charm.api.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
public class RateLimitService {

    private final ProxyManager<String> proxyManager;
    private final Map<RateLimitOperation, BucketConfiguration> configurations;

    public RateLimitService(
            @Qualifier("rateLimitProxyManager") ProxyManager<String> proxyManager,
            RateLimitProperties properties
    ) {
        this.proxyManager = proxyManager;
        this.configurations = new EnumMap<>(RateLimitOperation.class);
        configurations.put(RateLimitOperation.LOGIN, configuration(properties.login()));
        configurations.put(RateLimitOperation.REGISTRATION,
                configuration(properties.registration()));
        configurations.put(RateLimitOperation.REFRESH, configuration(properties.refresh()));
    }

    public ConsumptionProbe tryConsume(RateLimitOperation operation, String clientIp) {
        String key = "charm:rate-limit:" + operation.keyPart() + ":" + clientIp;

        return proxyManager.builder()
                .build(key, configurations.get(operation))
                .tryConsumeAndReturnRemaining(1);
    }

    private BucketConfiguration configuration(RateLimitProperties.Limit limit) {
        return BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth
                        .capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.refillPeriod()))
                .build();
    }
}
