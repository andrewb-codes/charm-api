package ru.andrewb.charm.api.user.photo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.andrewb.charm.api.JpaIntegrationTest;
import ru.andrewb.charm.api.storage.FileStorage;
import ru.andrewb.charm.api.user.domain.Gender;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.profile.UserNotFoundException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Import(UserPhotoService.class)
public class UserPhotoServiceTest extends JpaIntegrationTest {

    @MockitoBean
    private FileStorage fileStorage;

    @Autowired
    private UserPhotoService photoService;

    @Autowired
    private UserPhotoRepository photoRepository;

    @Test
    void uploadsPhotoAndSavesMetadata() {
        User user = createUser();
        byte[] content = photoContent();
        InputStream inputStream = new ByteArrayInputStream(content);
        when(fileStorage.createDownloadUrl(anyString()))
                .thenAnswer(invocation ->
                        "https://storage.test/"
                                + invocation.getArgument(0, String.class)
                );

        UserPhotoResponse response = photoService.upload(
                user.getId(),
                inputStream,
                content.length,
                "image/jpeg"
        );

        assertThat(response.id()).isNotNull();
        assertThat(response.position()).isZero();

        UserPhoto saved = photoRepository.findById(response.id()).orElseThrow();

        assertThat(saved.getUser().getId())
                .isEqualTo(user.getId());

        assertThat(saved.getPosition()).isZero();

        assertThat(saved.getObjectKey())
                .startsWith("users/" + user.getId() + "/")
                .endsWith(".jpg");
        assertThat(response.url())
                .isEqualTo(
                        "https://storage.test/" + saved.getObjectKey()
                );

        verify(fileStorage).upload(
                eq(saved.getObjectKey()),
                same(inputStream),
                eq((long) content.length),
                eq("image/jpeg")
        );
        verify(fileStorage).createDownloadUrl(saved.getObjectKey());
    }

    @Test
    void assignsNextPositionToNewPhoto() {
        User user = createUser();

        UserPhotoResponse first = uploadPhoto(user);
        UserPhotoResponse second = uploadPhoto(user);

        assertThat(first.position()).isZero();
        assertThat(second.position()).isEqualTo(1);

        assertThat(photoRepository
                .findAllByUser_IdOrderByPosition(user.getId()))
                .extracting(UserPhoto::getPosition)
                .containsExactly(0, 1);
    }

    @Test
    void rejectsUnsupportedContentTypeBeforeUpload() {
        User user = createUser();
        byte[] content = photoContent();

        assertThatThrownBy(() ->
                photoService.upload(
                        user.getId(),
                        new ByteArrayInputStream(content),
                        content.length,
                        "application/pdf"
                )
        )
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessage("Unsupported photo content type");

        assertThat(photoRepository.count()).isZero();
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsEmptyPhotoBeforeUpload() {
        User user = createUser();

        assertThatThrownBy(() ->
                photoService.upload(
                        user.getId(),
                        new ByteArrayInputStream(new byte[0]),
                        0,
                        "image/jpeg"
                )
        )
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessage("Photo file cannot be empty");

        assertThat(photoRepository.count()).isZero();
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsPhotoAboveLimit() {
        User user = createUser();

        IntStream.range(0, 6)
                .mapToObj(position ->
                        new UserPhoto(
                                user,
                                "users/%d/existing-%d.jpg".formatted(
                                        user.getId(),
                                        position
                                ),
                                position
                        )
                )
                .forEach(photoRepository::save);

        photoRepository.flush();

        byte[] content = photoContent();

        assertThatThrownBy(() ->
                photoService.upload(
                        user.getId(),
                        new ByteArrayInputStream(content),
                        content.length,
                        "image/jpeg"
                )
        )
                .isInstanceOf(PhotoLimitExceededException.class)
                .hasMessage("User cannot have more than 6 photos");

        assertThat(photoRepository.count()).isEqualTo(6);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsPhotoAboveSizeLimit() {
        User user = createUser();

        assertThatThrownBy(() ->
                photoService.upload(
                        user.getId(),
                        new ByteArrayInputStream(new byte[0]),
                        10L * 1024 * 1024 + 1,
                        "image/jpeg"
                )
        )
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessage("Photo file cannot exceed 10 MB");

        assertThat(photoRepository.count()).isZero();
        verifyNoInteractions(fileStorage);
    }

    @Test
    void deletesOwnPhotoFromDatabaseAndStorage() {
        User user = createUser();
        UserPhoto photo = photoRepository.saveAndFlush(
                new UserPhoto(
                        user,
                        "users/%d/photo.jpg".formatted(user.getId()),
                        0
                )
        );

        photoService.delete(user.getId(), photo.getId());

        assertThat(photoRepository.findById(photo.getId())).isEmpty();
        verify(fileStorage).delete(photo.getObjectKey());
    }

    @Test
    void rejectsDeletingUnknownPhoto() {
        User user = createUser();

        assertThatThrownBy(() -> photoService.delete(user.getId(), 999L))
                .isInstanceOf(UserPhotoNotFoundException.class)
                .hasMessage("Photo with id 999 was not found");

        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsDeletingAnotherUsersPhoto() {
        User owner = createUser("owner@example.com");
        User anotherUser = createUser("another@example.com");
        UserPhoto photo = photoRepository.saveAndFlush(
                new UserPhoto(
                        owner,
                        "users/%d/photo.jpg".formatted(owner.getId()),
                        0
                )
        );

        assertThatThrownBy(() ->
                photoService.delete(anotherUser.getId(), photo.getId())
        )
                .isInstanceOf(UserPhotoNotFoundException.class)
                .hasMessage(
                        "Photo with id %d was not found"
                                .formatted(photo.getId())
                );

        assertThat(photoRepository.findById(photo.getId())).isPresent();
        verifyNoInteractions(fileStorage);
    }

    @Test
    void returnsActiveUsersPhotosOrderedByPosition() {
        User user = createActiveUser("active@example.com");
        UserPhoto second = new UserPhoto(
                user,
                "users/%d/second.jpg".formatted(user.getId()),
                1
        );
        UserPhoto first = new UserPhoto(
                user,
                "users/%d/first.jpg".formatted(user.getId()),
                0
        );
        photoRepository.saveAllAndFlush(List.of(second, first));
        when(fileStorage.createDownloadUrl(anyString()))
                .thenAnswer(invocation ->
                        "https://storage.test/"
                                + invocation.getArgument(0, String.class)
                );

        List<UserPhotoResponse> photos =
                photoService.findAllByUserId(user.getId());

        assertThat(photos)
                .extracting(UserPhotoResponse::position)
                .containsExactly(0, 1);
        assertThat(photos)
                .extracting(UserPhotoResponse::id)
                .containsExactly(first.getId(), second.getId());
        assertThat(photos)
                .extracting(UserPhotoResponse::url)
                .containsExactly(
                        "https://storage.test/" + first.getObjectKey(),
                        "https://storage.test/" + second.getObjectKey()
                );
        verify(fileStorage).createDownloadUrl(first.getObjectKey());
        verify(fileStorage).createDownloadUrl(second.getObjectKey());
    }

    @Test
    void doesNotReturnPendingUsersPhotos() {
        User user = createUser();
        photoRepository.saveAndFlush(
                new UserPhoto(
                        user,
                        "users/%d/photo.jpg".formatted(user.getId()),
                        0
                )
        );

        assertThatThrownBy(() ->
                photoService.findAllByUserId(user.getId())
        )
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage(
                        "User with id '%d' was not found"
                                .formatted(user.getId())
                );

        verifyNoInteractions(fileStorage);
    }

    private UserPhotoResponse uploadPhoto(User user) {
        byte[] content = photoContent();

        return photoService.upload(
                user.getId(),
                new ByteArrayInputStream(content),
                content.length,
                "image/jpeg"
        );
    }

    private byte[] photoContent() {
        return "fake-photo-content"
                .getBytes(StandardCharsets.UTF_8);
    }

    private User createUser() {
        return createUser("andrew@example.com");
    }

    private User createUser(String email) {
        return userRepository.saveAndFlush(
                new User(
                        email,
                        "encoded-password"
                )
        );
    }

    private User createActiveUser(String email) {
        User user = new User(email, "encoded-password");
        user.updateProfile(
                "Andrew",
                null,
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                null
        );
        return userRepository.saveAndFlush(user);
    }
}
