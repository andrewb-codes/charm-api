package ru.andrewb.charm.api.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presignedRequest;

    private S3FileStorage fileStorage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                URI.create("https://s3.test.invalid"),
                "test-region",
                "test-bucket",
                "test-access-key",
                "test-secret-key",
                Duration.ofMinutes(15)
        );

        fileStorage = new S3FileStorage(
                s3Client,
                s3Presigner,
                properties
        );
    }

    @Test
    void uploadsObjectToConfiguredBucket() {
        byte[] content = "photo-content".getBytes(StandardCharsets.UTF_8);

        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenReturn(PutObjectResponse.builder().build());

        fileStorage.upload(
                "users/42/photo.jpg",
                new ByteArrayInputStream(content),
                content.length,
                "image/jpeg"
        );

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);

        verify(s3Client).putObject(
                requestCaptor.capture(),
                any(RequestBody.class)
        );

        PutObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket())
                .isEqualTo("test-bucket");

        assertThat(request.key())
                .isEqualTo("users/42/photo.jpg");

        assertThat(request.contentType())
                .isEqualTo("image/jpeg");

        assertThat(request.contentLength())
                .isEqualTo(content.length);
    }

    @Test
    void deletesObjectFromConfiguredBucket() {
        when(s3Client.deleteObject(
                any(DeleteObjectRequest.class)
        )).thenReturn(DeleteObjectResponse.builder().build());

        fileStorage.delete("users/42/photo.jpg");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);

        verify(s3Client).deleteObject(
                requestCaptor.capture()
        );

        DeleteObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket())
                .isEqualTo("test-bucket");

        assertThat(request.key())
                .isEqualTo("users/42/photo.jpg");
    }

    @Test
    void wrapsS3UploadFailure() {
        byte[] content = "photo-content".getBytes(StandardCharsets.UTF_8);

        SdkException sdkException = S3Exception.builder()
                .message("Access denied")
                .statusCode(403)
                .build();

        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenThrow(sdkException);

        assertThatThrownBy(() ->
                fileStorage.upload(
                        "users/42/photo.jpg",
                        new ByteArrayInputStream(content),
                        content.length,
                        "image/jpeg"
                )
        )
                .isInstanceOf(FileStorageException.class)
                .hasMessage(
                        "Failed to upload object: users/42/photo.jpg"
                )
                .hasCause(sdkException);
    }

    @Test
    void createsDownloadUrlForConfiguredBucket() throws Exception {
        when(s3Presigner.presignGetObject(
                any(GetObjectPresignRequest.class)
        )).thenReturn(presignedRequest);
        when(presignedRequest.url()).thenReturn(
                URI.create(
                        "https://s3.test.invalid/test-bucket/"
                                + "users/42/photo.jpg?signature=test"
                ).toURL()
        );

        String url = fileStorage.createDownloadUrl(
                "users/42/photo.jpg"
        );

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);

        verify(s3Presigner).presignGetObject(requestCaptor.capture());

        GetObjectPresignRequest presignRequest = requestCaptor.getValue();
        GetObjectRequest getObjectRequest =
                presignRequest.getObjectRequest();

        assertThat(presignRequest.signatureDuration())
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(getObjectRequest.bucket())
                .isEqualTo("test-bucket");
        assertThat(getObjectRequest.key())
                .isEqualTo("users/42/photo.jpg");
        assertThat(url)
                .isEqualTo(
                        "https://s3.test.invalid/test-bucket/"
                                + "users/42/photo.jpg?signature=test"
                );
    }

    @Test
    void wrapsDownloadUrlCreationFailure() {
        SdkException sdkException = S3Exception.builder()
                .message("Access denied")
                .statusCode(403)
                .build();

        when(s3Presigner.presignGetObject(
                any(GetObjectPresignRequest.class)
        )).thenThrow(sdkException);

        assertThatThrownBy(() ->
                fileStorage.createDownloadUrl("users/42/photo.jpg")
        )
                .isInstanceOf(FileStorageException.class)
                .hasMessage(
                        "Failed to create download URL: "
                                + "users/42/photo.jpg"
                )
                .hasCause(sdkException);
    }
}
