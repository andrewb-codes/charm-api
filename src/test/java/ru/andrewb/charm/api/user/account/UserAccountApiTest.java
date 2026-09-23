package ru.andrewb.charm.api.user.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.andrewb.charm.api.ApiIntegrationTest;
import ru.andrewb.charm.api.reaction.Reaction;
import ru.andrewb.charm.api.reaction.ReactionRepository;
import ru.andrewb.charm.api.reaction.ReactionType;
import ru.andrewb.charm.api.security.access.JwtTokenService;
import ru.andrewb.charm.api.security.refresh.RefreshTokenRepository;
import ru.andrewb.charm.api.security.refresh.RefreshTokenService;
import ru.andrewb.charm.api.storage.FileStorageException;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.photo.UserPhoto;
import ru.andrewb.charm.api.user.photo.UserPhotoRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAccountApiTest extends ApiIntegrationTest {

    private static final String CURRENT_PASSWORD = "current-password";
    private static final String NEW_PASSWORD = "new-password";

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private UserPhotoRepository photoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void rejectsAccountOperationsWithoutToken() throws Exception {
        mockMvc.perform(put("/api/v1/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordRequest(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequest(CURRENT_PASSWORD, "new@example.com")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changesPasswordAndRevokesRefreshTokens() throws Exception {
        User user = createUser("andrew@example.com", CURRENT_PASSWORD);
        refreshTokenService.issue(user);
        refreshTokenService.issue(user);
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(put("/api/v1/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordRequest(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User updated = userRepository.findById(user.getId()).orElseThrow();

        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPasswordHash()))
                .isTrue();
        assertThat(passwordEncoder.matches(CURRENT_PASSWORD, updated.getPasswordHash()))
                .isFalse();
        assertThat(refreshTokenRepository.findAllByUser_Id(user.getId()))
                .hasSize(2)
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    void rejectsIncorrectCurrentPasswordWithoutChangingAccount() throws Exception {
        User user = createUser("andrew@example.com", CURRENT_PASSWORD);
        String originalHash = user.getPasswordHash();
        refreshTokenService.issue(user);
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(put("/api/v1/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordRequest("incorrect-password", NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication failed"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(originalHash);
        assertThat(refreshTokenRepository.findAllByUser_Id(user.getId()))
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNull());
    }

    @Test
    void rejectsInvalidNewPassword() throws Exception {
        User user = createUser("andrew@example.com", CURRENT_PASSWORD);
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(put("/api/v1/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordRequest(CURRENT_PASSWORD, "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"));
    }

    @Test
    void changesEmailAndRevokesRefreshTokens() throws Exception {
        User user = createUser("andrew@example.com", CURRENT_PASSWORD);
        refreshTokenService.issue(user);
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(put("/api/v1/me/email")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequest(CURRENT_PASSWORD, "NEW@Example.COM")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(userRepository.findByEmail("andrew@example.com")).isEmpty();
        assertThat(userRepository.findByEmail("new@example.com"))
                .get()
                .extracting(User::getId)
                .isEqualTo(user.getId());
        assertThat(refreshTokenRepository.findAllByUser_Id(user.getId()))
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    void rejectsDuplicateEmailWithoutChangingAccount() throws Exception {
        User user = createUser("andrew@example.com", CURRENT_PASSWORD);
        createUser("existing@example.com", "another-password");
        refreshTokenService.issue(user);
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(put("/api/v1/me/email")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequest(CURRENT_PASSWORD, "EXISTING@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already exists"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail())
                .isEqualTo("andrew@example.com");
        assertThat(refreshTokenRepository.findAllByUser_Id(user.getId()))
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNull());
    }

    @Test
    void rejectsInvalidNewEmail() throws Exception {
        User user = createUser("andrew@example.com", CURRENT_PASSWORD);
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(put("/api/v1/me/email")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailRequest(CURRENT_PASSWORD, "not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"));
    }

    @Test
    void deletesCurrentUserWithRelatedDataAndStoredPhotos() throws Exception {
        User currentUser = createUser("andrew@example.com", CURRENT_PASSWORD);
        User anotherUser = createUser("maria@example.com", "another-password");

        reactionRepository.saveAllAndFlush(List.of(
                new Reaction(currentUser, anotherUser, ReactionType.LIKE),
                new Reaction(anotherUser, currentUser, ReactionType.LIKE)
        ));
        refreshTokenService.issue(currentUser);

        UserPhoto firstPhoto = new UserPhoto(
                currentUser,
                "users/%d/first.jpg".formatted(currentUser.getId()),
                0
        );
        UserPhoto secondPhoto = new UserPhoto(
                currentUser,
                "users/%d/second.jpg".formatted(currentUser.getId()),
                1
        );
        photoRepository.saveAllAndFlush(List.of(firstPhoto, secondPhoto));

        String accessToken = jwtTokenService.issue(currentUser);

        mockMvc.perform(delete("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(userRepository.findById(currentUser.getId())).isEmpty();
        assertThat(userRepository.findById(anotherUser.getId())).isPresent();
        assertThat(reactionRepository.count()).isZero();
        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(photoRepository.count()).isZero();

        verify(fileStorage).delete(firstPhoto.getObjectKey());
        verify(fileStorage).delete(secondPhoto.getObjectKey());
    }

    @Test
    void keepsUserWhenStoredPhotoCannotBeDeleted() throws Exception {
        User user = createUser("andrew@example.com", CURRENT_PASSWORD);
        UserPhoto photo = photoRepository.saveAndFlush(
                new UserPhoto(
                        user,
                        "users/%d/photo.jpg".formatted(user.getId()),
                        0
                )
        );
        String accessToken = jwtTokenService.issue(user);

        doThrow(new FileStorageException("S3 is unavailable", null))
                .when(fileStorage)
                .delete(photo.getObjectKey());

        mockMvc.perform(delete("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("File storage unavailable"));

        assertThat(userRepository.findById(user.getId())).isPresent();
        assertThat(photoRepository.findById(photo.getId())).isPresent();
    }

    private User createUser(String email, String password) {
        return userRepository.saveAndFlush(
                new User(email, passwordEncoder.encode(password))
        );
    }

    private String passwordRequest(String currentPassword, String newPassword) {
        return """
                {
                  "currentPassword": "%s",
                  "newPassword": "%s"
                }
                """.formatted(currentPassword, newPassword);
    }

    private String emailRequest(String currentPassword, String newEmail) {
        return """
                {
                  "currentPassword": "%s",
                  "newEmail": "%s"
                }
                """.formatted(currentPassword, newEmail);
    }
}
