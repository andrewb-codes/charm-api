package ru.andrewb.charm.api.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient(
            LettuceConnectionFactory connectionFactory
    ) {
        var server = connectionFactory.getStandaloneConfiguration();
        var settings = connectionFactory.getClientConfiguration();
        var uri = RedisURI.Builder.redis(server.getHostName(), server.getPort())
                .withDatabase(server.getDatabase())
                .withTimeout(settings.getCommandTimeout())
                .withSsl(settings.isUseSsl());
        if (server.getPassword().isPresent()) {
            if (server.getUsername() != null) {
                uri.withAuthentication(server.getUsername(), server.getPassword().get());
            } else {
                uri.withPassword(server.getPassword().get());
            }
        }
        RedisClient client = RedisClient.create(uri.build());
        client.setOptions(settings.getClientOptions().orElseGet(() -> ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(2)).build())
                .build()));
        return client;
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(
            @Qualifier("rateLimitRedisClient") RedisClient client
    ) {
        return client.connect(
                RedisCodec.of(
                        StringCodec.UTF8,
                        ByteArrayCodec.INSTANCE
                )
        );
    }

    @Bean
    public ProxyManager<String> rateLimitProxyManager(
            @Qualifier("rateLimitRedisConnection")
            StatefulRedisConnection<String, byte[]> connection
    ) {
        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(
                                        Duration.ofMinutes(1)
                                )
                )
                .requestTimeout(Duration.ofSeconds(2))
                .build();
    }
}
