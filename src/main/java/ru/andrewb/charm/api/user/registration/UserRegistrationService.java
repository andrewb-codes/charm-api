package ru.andrewb.charm.api.user.registration;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.persistence.UserRepository;

import java.util.Locale;
import java.util.Objects;

@Service
public class UserRegistrationService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(
            UserRepository repository,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Long register(String rawEmail, String rawPassword) {
        Objects.requireNonNull(rawEmail, "Email must not be null");
        Objects.requireNonNull(rawPassword, "Password must not be null");

        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(rawPassword);

        User user = new User(email, passwordHash);

        try {
            return repository.saveAndFlush(user).getId();
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException(email);
        }
    }
}
