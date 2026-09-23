package ru.andrewb.charm.api.user.account;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.security.refresh.RefreshTokenService;
import ru.andrewb.charm.api.storage.FileStorage;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.persistence.UserRepository;
import ru.andrewb.charm.api.user.photo.UserPhotoRepository;
import ru.andrewb.charm.api.user.profile.UserNotFoundException;
import ru.andrewb.charm.api.user.registration.EmailAlreadyExistsException;

import java.util.List;
import java.util.Locale;

@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final UserPhotoRepository photoRepository;
    private final FileStorage fileStorage;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserAccountService(
            UserRepository userRepository,
            UserPhotoRepository photoRepository,
            FileStorage fileStorage,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.fileStorage = fileStorage;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public void changePassword(
            Long userId,
            String currentPassword,
            String newPassword
    ) {
        User user = findUser(userId);
        verifyPassword(user, currentPassword);

        user.changePasswordHash(passwordEncoder.encode(newPassword));

        userRepository.saveAndFlush(user);
        refreshTokenService.revokeAll(userId);
    }

    @Transactional
    public void changeEmail(
            Long userId,
            String currentPassword,
            String rawNewEmail
    ) {
        User user = findUser(userId);
        verifyPassword(user, currentPassword);

        String newEmail = rawNewEmail.trim().toLowerCase(Locale.ROOT);

        if (user.getEmail().equals(newEmail)) {
            return;
        }

        user.changeEmail(newEmail);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException(newEmail);
        }

        refreshTokenService.revokeAll(userId);
    }

    @Transactional
    public void delete(Long userId) {
        User user = findUser(userId);

        List<String> objectKeys = photoRepository.findObjectKeysByUserId(userId);

        userRepository.delete(user);
        userRepository.flush();

        objectKeys.forEach(fileStorage::delete);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void verifyPassword(
            User user,
            String currentPassword
    ) {
        if (!passwordEncoder.matches(
                currentPassword,
                user.getPasswordHash()
        )) {
            throw new BadCredentialsException("Current password is incorrect");
        }
    }
}
