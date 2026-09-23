package ru.andrewb.charm.api.user.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.persistence.UserRepository;


@Service
public class UserProfileService {

    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public CurrentUserResponse update(
            Long userId,
            UserProfileUpdateRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getVersion() != request.version()) {
            throw new UserVersionConflictException(
                    userId,
                    request.version(),
                    user.getVersion()
            );
        }

        user.updateProfile(
                request.firstName().trim(),
                normalizeOptional(request.lastName()),
                request.birthDate(),
                request.gender(),
                normalizeOptional(request.bio())
        );

        userRepository.saveAndFlush(user);

        return CurrentUserResponse.from(user);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
