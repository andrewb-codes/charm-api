package ru.andrewb.charm.api.reaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.eligibility.UserEligibilityService;
import ru.andrewb.charm.api.user.persistence.UserRepository;
import ru.andrewb.charm.api.user.profile.UserNotFoundException;

import java.util.Objects;

@Service
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final UserEligibilityService userEligibilityService;

    public ReactionService(
            ReactionRepository reactionRepository,
            UserRepository userRepository,
            UserEligibilityService userEligibilityService
    ) {
        this.reactionRepository = reactionRepository;
        this.userRepository = userRepository;
        this.userEligibilityService = userEligibilityService;
    }

    @Transactional
    public ReactionResult react(
            Long senderId,
            Long receiverId,
            ReactionType type
    ) {
        User sender = userEligibilityService.requireActionAllowed(senderId);

        if (Objects.equals(senderId, receiverId)) {
            throw new SelfReactionNotAllowedException();
        }

        User receiver = findAvailableReceiver(receiverId);

        Reaction reaction = reactionRepository
                .findBySender_IdAndReceiver_Id(senderId, receiverId)
                .map(existing -> {
                    existing.changeType(type);
                    return existing;
                })
                .orElseGet(() -> new Reaction(sender, receiver, type));

        Reaction saved = reactionRepository.saveAndFlush(reaction);

        boolean matched =
                type == ReactionType.LIKE
                        && reactionRepository
                        .existsBySender_IdAndReceiver_IdAndType(
                                receiverId,
                                senderId,
                                ReactionType.LIKE
                        );

        return new ReactionResult(
                saved.getId(),
                saved.getType(),
                matched
        );
    }

    @Transactional
    public void removeReaction(
            Long senderId,
            Long receiverId
    ) {
        userEligibilityService.requireActionAllowed(senderId);

        reactionRepository
                .deleteBySender_IdAndReceiver_Id(
                        senderId,
                        receiverId
                );
    }

    private User findAvailableReceiver(Long userId) {
        return userRepository
                .findByIdAndProfileStatusAndAccountStatus(
                        userId,
                        ProfileStatus.ACTIVE,
                        AccountStatus.ACTIVE
                )
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
