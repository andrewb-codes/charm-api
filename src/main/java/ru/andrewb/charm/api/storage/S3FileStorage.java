package ru.andrewb.charm.api.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;

@Component
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public S3FileStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            StorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public void upload(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(inputStream, contentLength)
            );
        } catch (SdkException exception) {
            throw new FileStorageException(
                    "Failed to upload object: " + objectKey,
                    exception
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            throw new FileStorageException(
                    "Failed to delete object: " + objectKey,
                    exception
            );
        }
    }

    @Override
    public String createDownloadUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.downloadUrlTtl())
                .getObjectRequest(getObjectRequest)
                .build();

        try {
            return s3Presigner
                    .presignGetObject(presignRequest)
                    .url()
                    .toString();
        } catch (SdkException exception) {
            throw new FileStorageException(
                    "Failed to create download URL: " + objectKey,
                    exception
            );
        }
    }
}
