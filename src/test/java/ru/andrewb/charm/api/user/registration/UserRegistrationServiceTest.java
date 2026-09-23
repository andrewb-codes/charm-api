package ru.andrewb.charm.api.user.registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.andrewb.charm.api.JpaIntegrationTest;
import ru.andrewb.charm.api.config.PasswordConfiguration;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.domain.UserRole;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.registration.EmailAlreadyExistsException;
import ru.andrewb.charm.api.user.registration.UserRegistrationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({
        UserRegistrationService.class,
        PasswordConfiguration.class
})
class UserRegistrationServiceTest extends JpaIntegrationTest {

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersUserWithNormalizedEmailAndEncodedPassword() {
        Long id = registrationService.register(
                "  Andrew@Example.COM  ",
                "secret-password"
        );

        User user = userRepository.findById(id).orElseThrow();

        assertThat(user.getEmail()).isEqualTo("andrew@example.com");
        assertThat(user.getPasswordHash()).isNotEqualTo("secret-password");
        assertThat(passwordEncoder.matches(
                "secret-password",
                user.getPasswordHash()
        )).isTrue();
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getProfileStatus()).isEqualTo(ProfileStatus.PENDING);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        registrationService.register(
                "andrew@example.com",
                "first-password"
        );

        assertThatThrownBy(() ->
                registrationService.register(
                        "ANDREW@example.com",
                        "second-password"
                )
        ).isInstanceOf(EmailAlreadyExistsException.class);
    }
}
