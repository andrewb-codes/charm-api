package ru.andrewb.charm.api.user.photo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import ru.andrewb.charm.api.JpaIntegrationTest;
import ru.andrewb.charm.api.user.domain.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPhotoRepositoryTest extends JpaIntegrationTest {

    @Autowired
    private UserPhotoRepository photoRepository;

    @Test
    void returnsUserPhotosOrderedByPosition() {
        User user = createUser("andrew@example.com");

        photoRepository.saveAllAndFlush(List.of(
                new UserPhoto(user, "users/1/second.jpg", 1),
                new UserPhoto(user, "users/1/first.jpg", 0)
        ));

        List<UserPhoto> photos = photoRepository.findAllByUser_IdOrderByPosition(user.getId());

        assertThat(photos)
                .extracting(UserPhoto::getObjectKey)
                .containsExactly(
                        "users/1/first.jpg",
                        "users/1/second.jpg"
                );
    }

    @Test
    void rejectsDuplicatePositionForSameUser() {
        User user = createUser("andrew@example.com");

        photoRepository.saveAndFlush(
                new UserPhoto(user, "users/1/first.jpg", 0)
        );

        assertThatThrownBy(() ->
                photoRepository.saveAndFlush(
                        new UserPhoto(user, "users/1/another.jpg", 0)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User createUser(String email) {
        return userRepository.saveAndFlush(
                new User(email, "encoded-password")
        );
    }

    @Test
    void rejectsDuplicateObjectKey() {
        User firstUser = createUser("first@example.com");
        User secondUser = createUser("second@example.com");

        photoRepository.saveAndFlush(
                new UserPhoto(firstUser, "users/shared/photo.jpg", 0)
        );

        assertThatThrownBy(() ->
                photoRepository.saveAndFlush(
                        new UserPhoto(secondUser, "users/shared/photo.jpg", 0)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
