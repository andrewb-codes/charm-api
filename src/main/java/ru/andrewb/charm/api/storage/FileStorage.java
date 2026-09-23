package ru.andrewb.charm.api.storage;

import java.io.InputStream;

public interface FileStorage {

    void upload(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    );

    void delete(String objectKey);

    String createDownloadUrl(String objectKey);
}
