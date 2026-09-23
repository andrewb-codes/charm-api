package ru.andrewb.charm.api.reaction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import ru.andrewb.charm.api.JpaIntegrationTest;
import ru.andrewb.charm.api.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactionRepositoryTest extends JpaIntegrationTest {

    @Autowired
    private ReactionRepository reactionRepository;

    @Test
    void savesReactionBetweenUsers() {
        User sender = createUser("sender@example.com");
        User receiver = createUser("receiver@example.com");

        Reaction reaction = reactionRepository.saveAndFlush(
                new Reaction(sender, receiver, ReactionType.LIKE)
        );

        assertThat(reaction.getId()).isNotNull();
        assertThat(reaction.getType()).isEqualTo(ReactionType.LIKE);
        assertThat(reaction.getCreatedAt()).isNotNull();
        assertThat(
                reactionRepository.existsBySender_IdAndReceiver_IdAndType(
                        sender.getId(),
                        receiver.getId(),
                        ReactionType.LIKE
                )
        ).isTrue();
    }

    @Test
    void rejectsSecondReactionForSameUsers() {
        User sender = createUser("sender@example.com");
        User receiver = createUser("receiver@example.com");

        reactionRepository.saveAndFlush(
                new Reaction(sender, receiver, ReactionType.PASS)
        );

        assertThatThrownBy(() ->
                reactionRepository.saveAndFlush(
                        new Reaction(sender, receiver, ReactionType.LIKE)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsReactionToSelf() {
        User user = createUser("andrew@example.com");

        assertThatThrownBy(() ->
                reactionRepository.saveAndFlush(
                        new Reaction(user, user, ReactionType.LIKE)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User createUser(String email) {
        return userRepository.saveAndFlush(
                new User(email, "encoded-password")
        );
    }
}
