package ru.andrewb.charm.api.user.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import ru.andrewb.charm.api.JpaIntegrationTest;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.domain.UserRole;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest extends JpaIntegrationTest {

    @Test
    void savesAndFindsUserByEmail() {
        User user = new User(
                "andrew@example.com",
                "encoded-password"
        );

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.getProfileStatus()).isEqualTo(ProfileStatus.PENDING);
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(userRepository.findByEmail("andrew@example.com")).containsSame(saved);
    }

    @Test
    void rejectsEmailDuplicateIgnoringCase() {
        userRepository.saveAndFlush(new User(
                "andrew@example.com",
                "encoded-password"
        ));

        User duplicate = new User(
                "ANDREW@example.com",
                "another-encoded-password"
        );

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
