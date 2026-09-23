package ru.andrewb.charm.api.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Configuration {

    @Bean
    S3Client s3Client(StorageProperties properties) {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(
                        properties.accessKey(),
                        properties.secretKey()
                );

        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(
                        software.amazon.awssdk.services.s3.S3Configuration
                                .builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties properties) {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(
                        properties.accessKey(),
                        properties.secretKey()
                );

        return S3Presigner.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(
                        software.amazon.awssdk.services.s3.S3Configuration
                                .builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();
    }
}
