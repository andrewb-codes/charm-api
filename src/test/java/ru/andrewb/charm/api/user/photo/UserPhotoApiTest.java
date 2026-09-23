package ru.andrewb.charm.api.user.photo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import ru.andrewb.charm.api.ApiIntegrationTest;
import ru.andrewb.charm.api.security.access.JwtTokenService;
import ru.andrewb.charm.api.user.domain.Gender;
import ru.andrewb.charm.api.user.domain.User;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserPhotoApiTest extends ApiIntegrationTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserPhotoRepository photoRepository;

    @Test
    void uploadsPhotoForAuthenticatedUser() throws Exception {
        User user = createUser();
        String accessToken = jwtTokenService.issue(user);
        byte[] content = photoContent();
        when(fileStorage.createDownloadUrl(anyString()))
                .thenAnswer(invocation ->
                        "https://storage.test/"
                                + invocation.getArgument(0, String.class)
                );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                content
        );

        mockMvc.perform(multipart("/api/v1/me/photos")
                        .file(file)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.position").value(0))
                .andExpect(jsonPath("$.url").isString());

        List<UserPhoto> photos = photoRepository
                .findAllByUser_IdOrderByPosition(user.getId());

        assertThat(photos).hasSize(1);

        UserPhoto saved = photos.getFirst();

        assertThat(saved.getObjectKey())
                .startsWith("users/" + user.getId() + "/")
                .endsWith(".jpg");

        ArgumentCaptor<String> objectKeyCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(fileStorage).upload(
                objectKeyCaptor.capture(),
                any(InputStream.class),
                eq((long) content.length),
                eq("image/jpeg")
        );

        assertThat(objectKeyCaptor.getValue())
                .isEqualTo(saved.getObjectKey());
        verify(fileStorage).createDownloadUrl(saved.getObjectKey());
    }

    @Test
    void rejectsPhotoUploadWithoutAccessToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                photoContent()
        );

        mockMvc.perform(multipart("/api/v1/me/photos")
                        .file(file))
                .andExpect(status().isUnauthorized());

        assertThat(photoRepository.count()).isZero();
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsUnsupportedPhotoContentType() throws Exception {
        User user = createUser();
        String accessToken = jwtTokenService.issue(user);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                photoContent()
        );

        mockMvc.perform(multipart("/api/v1/me/photos")
                        .file(file)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Invalid photo"))
                .andExpect(jsonPath("$.detail")
                        .value("Unsupported photo content type"));

        assertThat(photoRepository.count()).isZero();
        verifyNoInteractions(fileStorage);
    }

    @Test
    void rejectsPhotoAboveUserLimit() throws Exception {
        User user = createUser();
        String accessToken = jwtTokenService.issue(user);

        IntStream.range(0, 6)
                .mapToObj(position -> new UserPhoto(
                        user,
                        "users/%d/existing-%d.jpg".formatted(
                                user.getId(),
                                position
                        ),
                        position
                ))
                .forEach(photoRepository::save);

        photoRepository.flush();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "another.jpg",
                "image/jpeg",
                photoContent()
        );

        mockMvc.perform(multipart("/api/v1/me/photos")
                        .file(file)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Photo limit exceeded"))
                .andExpect(jsonPath("$.detail")
                        .value("User cannot have more than 6 photos"));

        assertThat(photoRepository.count()).isEqualTo(6);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void deletesPhotoForAuthenticatedOwner() throws Exception {
        User user = createUser();
        UserPhoto photo = createPhoto(user);
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(delete("/api/v1/me/photos/{photoId}", photo.getId())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isNoContent());

        assertThat(photoRepository.findById(photo.getId())).isEmpty();
        verify(fileStorage).delete(photo.getObjectKey());
    }

    @Test
    void rejectsPhotoDeletionWithoutAccessToken() throws Exception {
        User user = createUser();
        UserPhoto photo = createPhoto(user);

        mockMvc.perform(delete(
                        "/api/v1/me/photos/{photoId}",
                        photo.getId()
                ))
                .andExpect(status().isUnauthorized());

        assertThat(photoRepository.findById(photo.getId())).isPresent();
        verifyNoInteractions(fileStorage);
    }

    @Test
    void returnsNotFoundWhenDeletingAnotherUsersPhoto() throws Exception {
        User owner = createUser("owner@example.com");
        User anotherUser = createUser("another@example.com");
        UserPhoto photo = createPhoto(owner);
        String accessToken = jwtTokenService.issue(anotherUser);

        mockMvc.perform(delete("/api/v1/me/photos/{photoId}", photo.getId())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Photo not found"))
                .andExpect(jsonPath("$.detail").value(
                        "Photo with id %d was not found"
                                .formatted(photo.getId())
                ));

        assertThat(photoRepository.findById(photo.getId())).isPresent();
        verifyNoInteractions(fileStorage);
    }

    @Test
    void returnsNotFoundWhenDeletingUnknownPhoto() throws Exception {
        User user = createUser();
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(delete("/api/v1/me/photos/{photoId}", 999L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Photo not found"))
                .andExpect(jsonPath("$.detail")
                        .value("Photo with id 999 was not found"));

        verifyNoInteractions(fileStorage);
    }

    @Test
    void returnsActiveUsersPhotosWithoutAuthentication() throws Exception {
        User user = createActiveUser("active@example.com");
        UserPhoto second = createPhoto(user, 1);
        UserPhoto first = createPhoto(user, 0);
        when(fileStorage.createDownloadUrl(anyString()))
                .thenAnswer(invocation ->
                        "https://storage.test/"
                                + invocation.getArgument(0, String.class)
                );

        mockMvc.perform(get(
                        "/api/v1/users/{userId}/photos",
                        user.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[0].url").value(
                        "https://storage.test/" + first.getObjectKey()
                ))
                .andExpect(jsonPath("$[1].id").value(second.getId()))
                .andExpect(jsonPath("$[1].position").value(1))
                .andExpect(jsonPath("$[1].url").value(
                        "https://storage.test/" + second.getObjectKey()
                ));

        verify(fileStorage).createDownloadUrl(first.getObjectKey());
        verify(fileStorage).createDownloadUrl(second.getObjectKey());
    }

    @Test
    void doesNotReturnPendingUsersPhotos() throws Exception {
        User user = createUser();
        createPhoto(user);

        mockMvc.perform(get(
                        "/api/v1/users/{userId}/photos",
                        user.getId()
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));

        verifyNoInteractions(fileStorage);
    }

    @Test
    void returnsNotFoundForUnknownUsersPhotos() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/users/{userId}/photos",
                        999999L
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));

        verifyNoInteractions(fileStorage);
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

    private UserPhoto createPhoto(User user) {
        return createPhoto(user, 0);
    }

    private UserPhoto createPhoto(User user, int position) {
        return photoRepository.saveAndFlush(
                new UserPhoto(
                        user,
                        "users/%d/photo-%d.jpg".formatted(
                                user.getId(),
                                position
                        ),
                        position
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

    private byte[] photoContent() {
        return "fake-photo-content"
                .getBytes(StandardCharsets.UTF_8);
    }
}
