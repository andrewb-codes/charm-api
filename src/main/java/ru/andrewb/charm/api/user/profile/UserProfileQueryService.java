package ru.andrewb.charm.api.user.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.persistence.UserRepository;

@Service
public class UserProfileQueryService {

    private final UserRepository userRepository;

    public UserProfileQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PublicUserResponse findPublicById(Long id) {
        return userRepository.findByIdAndProfileStatusAndAccountStatus(
                        id,
                        ProfileStatus.ACTIVE,
                        AccountStatus.ACTIVE
                )
                .map(PublicUserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse findCurrentById(Long id) {
        return userRepository.findById(id)
                .map(CurrentUserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
