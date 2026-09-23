package ru.andrewb.charm.api.match;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.reaction.ReactionRepository;
import ru.andrewb.charm.api.reaction.ReactionType;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.eligibility.UserEligibilityService;
import ru.andrewb.charm.api.user.profile.PublicUserResponse;

import java.util.List;

@Service
public class MatchQueryService {

    private final ReactionRepository reactionRepository;
    private final UserEligibilityService userEligibilityService;

    public MatchQueryService(
            ReactionRepository reactionRepository,
            UserEligibilityService userEligibilityService
    ) {
        this.reactionRepository = reactionRepository;
        this.userEligibilityService = userEligibilityService;
    }

    @Transactional(readOnly = true)
    public List<PublicUserResponse> findMatches(Long userId) {
        userEligibilityService.requireActionAllowed(userId);

        return reactionRepository
                .findMatchedUsers(
                        userId,
                        ReactionType.LIKE,
                        ProfileStatus.ACTIVE,
                        AccountStatus.ACTIVE
                )
                .stream()
                .map(PublicUserResponse::from)
                .toList();
    }
}
