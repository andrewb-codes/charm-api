package ru.andrewb.charm.api.user.photo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.storage.FileStorage;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.persistence.UserRepository;
import ru.andrewb.charm.api.user.profile.UserNotFoundException;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserPhotoService {

    private static final int MAX_PHOTOS = 6;
    private static final long MAX_PHOTO_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final UserRepository userRepository;
    private final UserPhotoRepository photoRepository;
    private final FileStorage fileStorage;

    public UserPhotoService(
            UserRepository userRepository,
            UserPhotoRepository photoRepository,
            FileStorage fileStorage
    ) {
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.fileStorage = fileStorage;
    }

    @Transactional(readOnly = true)
    public List<UserPhotoResponse> findAllByUserId(Long userId) {
        userRepository
                .findByIdAndProfileStatusAndAccountStatus(
                        userId,
                        ProfileStatus.ACTIVE,
                        AccountStatus.ACTIVE
                )
                .orElseThrow(() -> new UserNotFoundException(userId));

        return photoRepository
                .findAllByUser_IdOrderByPosition(userId)
                .stream()
                .map(photo -> {
                    String url = fileStorage.createDownloadUrl(photo.getObjectKey());
                    return UserPhotoResponse.from(photo, url);
                })
                .toList();
    }

    @Transactional
    public UserPhotoResponse upload(
            Long userId,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) {
        validate(contentLength, contentType);

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (photoRepository.countByUser_Id(userId) >= MAX_PHOTOS) {
            throw new PhotoLimitExceededException(MAX_PHOTOS);
        }

        int position = photoRepository
                .findFirstByUser_IdOrderByPositionDesc(userId)
                .map(photo -> photo.getPosition() + 1)
                .orElse(0);

        String objectKey = createObjectKey(userId, contentType);

        fileStorage.upload(
                objectKey,
                inputStream,
                contentLength,
                contentType
        );

        try {
            UserPhoto photo = photoRepository.saveAndFlush(
                    new UserPhoto(
                            user,
                            objectKey,
                            position
                    )
            );

            String url = fileStorage.createDownloadUrl(photo.getObjectKey());

            return UserPhotoResponse.from(photo, url);
        } catch (RuntimeException exception) {
            fileStorage.delete(objectKey);
            throw exception;
        }
    }

    @Transactional
    public void delete(
            Long userId,
            Long photoId
    ) {
        UserPhoto photo = photoRepository
                .findByIdAndUser_Id(photoId, userId)
                .orElseThrow(() -> new UserPhotoNotFoundException(photoId));

        photoRepository.delete(photo);
        photoRepository.flush();

        fileStorage.delete(photo.getObjectKey());
    }

    private void validate(
            long contentLength,
            String contentType
    ) {
        if (contentLength <= 0) {
            throw new InvalidPhotoException("Photo file cannot be empty");
        }

        if (contentLength > MAX_PHOTO_SIZE_BYTES) {
            throw new InvalidPhotoException("Photo file cannot exceed 10 MB");
        }

        if (!EXTENSIONS.containsKey(contentType)) {
            throw new InvalidPhotoException("Unsupported photo content type");
        }
    }

    private String createObjectKey(
            Long userId,
            String contentType
    ) {
        String extension = EXTENSIONS.get(contentType);

        return "users/%d/%s.%s".formatted(
                userId,
                UUID.randomUUID(),
                extension
        );
    }
}
