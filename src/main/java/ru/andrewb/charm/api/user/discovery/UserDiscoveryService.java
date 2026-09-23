package ru.andrewb.charm.api.user.discovery;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.persistence.UserRepository;
import ru.andrewb.charm.api.user.profile.PublicUserResponse;

import java.util.List;

@Service
public class UserDiscoveryService {

    private final UserRepository userRepository;

    public UserDiscoveryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserDiscoveryResponse findUsers(
            Long currentUserId,
            int page,
            int size
    ) {
        Slice<User> result = userRepository.findDiscoveryUsers(
                currentUserId,
                ProfileStatus.ACTIVE,
                AccountStatus.ACTIVE,
                PageRequest.of(page, size)
        );

        List<PublicUserResponse> users = result
                .getContent()
                .stream()
                .map(PublicUserResponse::from)
                .toList();

        return new UserDiscoveryResponse(
                users,
                result.getNumber(),
                result.getSize(),
                result.hasNext()
        );
    }
}
