package ru.andrewb.charm.api.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;

class S3ConnectionTest {

    @Test
    @EnabledIfEnvironmentVariable(
            named = "RUN_S3_TEST",
            matches = "true"
    )
    void connectsToConfiguredBucket() {
        StorageProperties properties = new StorageProperties(
                URI.create(requiredEnvironmentVariable("S3_ENDPOINT")),
                requiredEnvironmentVariable("S3_REGION"),
                requiredEnvironmentVariable("S3_BUCKET"),
                requiredEnvironmentVariable("S3_ACCESS_KEY"),
                requiredEnvironmentVariable("S3_SECRET_KEY"),
                Duration.ofMinutes(15)
        );

        try (S3Client s3Client = new S3Configuration().s3Client(properties)) {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(properties.bucket())
                    .build();

            assertThatCode(() ->
                    s3Client.headBucket(request)
            ).doesNotThrowAnyException();
        }
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable is missing: " + name
            );
        }

        return value;
    }
}
