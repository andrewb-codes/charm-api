package ru.andrewb.charm.api.user.eligibility;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.user.account.AccountBlockedException;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.persistence.UserRepository;
import ru.andrewb.charm.api.user.profile.ProfileIncompleteException;
import ru.andrewb.charm.api.user.profile.UserNotFoundException;

@Service
public class UserEligibilityService {

    private final UserRepository userRepository;

    public UserEligibilityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User requireActionAllowed(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getAccountStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException();
        }

        if (user.getProfileStatus() == ProfileStatus.PENDING) {
            throw new ProfileIncompleteException();
        }

        return user;
    }
}
