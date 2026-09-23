package ru.andrewb.charm.api.reaction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import ru.andrewb.charm.api.JpaIntegrationTest;
import ru.andrewb.charm.api.user.domain.Gender;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.eligibility.UserEligibilityService;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({ReactionService.class, UserEligibilityService.class})
class ReactionServiceTest extends JpaIntegrationTest {

    @Autowired
    private ReactionService reactionService;

    @Autowired
    private ReactionRepository reactionRepository;

    @Test
    void createsLikeWithoutMatch() {
        User sender = createActiveUser("sender@example.com", "Andrew");
        User receiver = createActiveUser("receiver@example.com", "Maria");

        ReactionResult result = reactionService.react(
                sender.getId(),
                receiver.getId(),
                ReactionType.LIKE
        );

        assertThat(result.reactionId()).isNotNull();
        assertThat(result.type()).isEqualTo(ReactionType.LIKE);
        assertThat(result.matched()).isFalse();
    }

    @Test
    void reportsMatchOnlyForMutualLikes() {
        User andrew = createActiveUser("andrew@example.com", "Andrew");
        User maria = createActiveUser("maria@example.com", "Maria");

        ReactionResult first = reactionService.react(
                andrew.getId(), maria.getId(), ReactionType.LIKE
        );
        ReactionResult second = reactionService.react(
                maria.getId(), andrew.getId(), ReactionType.LIKE
        );

        assertThat(first.matched()).isFalse();
        assertThat(second.matched()).isTrue();
        assertThat(reactionRepository.count()).isEqualTo(2);
    }

    @Test
    void passDoesNotCreateMatch() {
        User andrew = createActiveUser("andrew@example.com", "Andrew");
        User maria = createActiveUser("maria@example.com", "Maria");

        reactionService.react(
                andrew.getId(), maria.getId(), ReactionType.LIKE
        );
        ReactionResult result = reactionService.react(
                maria.getId(), andrew.getId(), ReactionType.PASS
        );

        assertThat(result.type()).isEqualTo(ReactionType.PASS);
        assertThat(result.matched()).isFalse();
    }

    @Test
    void changesExistingReactionInsteadOfCreatingSecondOne() {
        User sender = createActiveUser("sender@example.com", "Andrew");
        User receiver = createActiveUser("receiver@example.com", "Maria");

        ReactionResult pass = reactionService.react(
                sender.getId(), receiver.getId(), ReactionType.PASS
        );
        ReactionResult like = reactionService.react(
                sender.getId(), receiver.getId(), ReactionType.LIKE
        );

        assertThat(like.reactionId()).isEqualTo(pass.reactionId());
        assertThat(like.type()).isEqualTo(ReactionType.LIKE);
        assertThat(reactionRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsReactionToSelf() {
        User user = createActiveUser("andrew@example.com", "Andrew");

        assertThatThrownBy(() ->
                reactionService.react(
                        user.getId(),
                        user.getId(),
                        ReactionType.LIKE
                )
        )
                .isInstanceOf(SelfReactionNotAllowedException.class)
                .hasMessage("User cannot react to themselves");

        assertThat(reactionRepository.count()).isZero();
    }

    private User createActiveUser(String email, String firstName) {
        User user = new User(email, "encoded-password");
        user.updateProfile(
                firstName,
                null,
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                null
        );
        return userRepository.saveAndFlush(user);
    }
}
