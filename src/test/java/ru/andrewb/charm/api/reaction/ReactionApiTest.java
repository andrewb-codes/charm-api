package ru.andrewb.charm.api.reaction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.andrewb.charm.api.ApiIntegrationTest;
import ru.andrewb.charm.api.security.access.JwtTokenService;
import ru.andrewb.charm.api.user.domain.Gender;
import ru.andrewb.charm.api.user.domain.User;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReactionApiTest extends ApiIntegrationTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ReactionRepository reactionRepository;

    @Test
    void rejectsReactionWithoutToken() throws Exception {
        User receiver = createActiveUser("receiver@example.com", "Maria");

        mockMvc.perform(put(
                            "/api/v1/users/{receiverId}/reaction",
                            receiver.getId()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "LIKE"}
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(reactionRepository.count()).isZero();
    }

    @Test
    void createsLikeFromAuthenticatedUser() throws Exception {
        User sender = createActiveUser("sender@example.com", "Andrew");
        User receiver = createActiveUser("receiver@example.com", "Maria");
        String accessToken = jwtTokenService.issue(sender);

        mockMvc.perform(put(
                            "/api/v1/users/{receiverId}/reaction",
                            receiver.getId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "LIKE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactionId").isNumber())
                .andExpect(jsonPath("$.type").value("LIKE"))
                .andExpect(jsonPath("$.matched").value(false));

        assertThat(
                reactionRepository.existsBySender_IdAndReceiver_IdAndType(
                        sender.getId(),
                        receiver.getId(),
                        ReactionType.LIKE
                )
        ).isTrue();
    }

    @Test
    void changesPassToLikeWithoutCreatingSecondReaction() throws Exception {
        User sender = createActiveUser("sender@example.com", "Andrew");
        User receiver = createActiveUser("receiver@example.com", "Maria");
        Reaction existing = reactionRepository.saveAndFlush(
                new Reaction(sender, receiver, ReactionType.PASS)
        );
        String accessToken = jwtTokenService.issue(sender);

        mockMvc.perform(put(
                            "/api/v1/users/{receiverId}/reaction",
                            receiver.getId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "LIKE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactionId").value(existing.getId()))
                .andExpect(jsonPath("$.type").value("LIKE"));

        assertThat(reactionRepository.count()).isEqualTo(1);
    }

    @Test
    void returnsMatchForMutualLikes() throws Exception {
        User andrew = createActiveUser("andrew@example.com", "Andrew");
        User maria = createActiveUser("maria@example.com", "Maria");
        reactionRepository.saveAndFlush(
                new Reaction(andrew, maria, ReactionType.LIKE)
        );
        String accessToken = jwtTokenService.issue(maria);

        mockMvc.perform(put(
                            "/api/v1/users/{receiverId}/reaction",
                            andrew.getId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "LIKE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true));
    }

    @Test
    void rejectsReactionToSelf() throws Exception {
        User user = createActiveUser("andrew@example.com", "Andrew");
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(put(
                            "/api/v1/users/{receiverId}/reaction",
                            user.getId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "LIKE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Self-reaction is not allowed"));
    }

    @Test
    void rejectsReactionFromPendingUser() throws Exception {
        User sender = userRepository.saveAndFlush(
                new User("sender@example.com", "encoded-password")
        );
        User receiver = createActiveUser("receiver@example.com", "Maria");
        String accessToken = jwtTokenService.issue(sender);

        mockMvc.perform(put(
                            "/api/v1/users/{receiverId}/reaction",
                            receiver.getId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "LIKE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Profile is incomplete"));

        assertThat(reactionRepository.count()).isZero();
    }

    @Test
    void rejectsReactionFromBlockedUser() throws Exception {
        User sender = createActiveUser("sender@example.com", "Andrew");
        User receiver = createActiveUser("receiver@example.com", "Maria");
        String accessToken = jwtTokenService.issue(sender);
        sender.block();
        userRepository.saveAndFlush(sender);

        mockMvc.perform(put(
                            "/api/v1/users/{receiverId}/reaction",
                            receiver.getId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "LIKE"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Account is blocked"));

        assertThat(reactionRepository.count()).isZero();
    }

    @Test
    void hidesPendingReceiverBehindNotFound() throws Exception {
        User sender = createActiveUser("sender@example.com", "Andrew");
        User receiver = userRepository.saveAndFlush(
                new User("receiver@example.com", "encoded-password")
        );
        String accessToken = jwtTokenService.issue(sender);

        mockMvc.perform(put(
                            "/api/v1/users/{receiverId}/reaction",
                            receiver.getId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "LIKE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));

        assertThat(reactionRepository.count()).isZero();
    }

    @Test
    void hidesBlockedReceiverBehindNotFound() throws Exception {
        User sender = createActiveUser("sender@example.com", "Andrew");
        User receiver = createActiveUser("receiver@example.com", "Maria");
        receiver.block();
        userRepository.saveAndFlush(receiver);
        String accessToken = jwtTokenService.issue(sender);

        mockMvc.perform(put(
                            "/api/v1/users/{receiverId}/reaction",
                            receiver.getId()
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "LIKE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));

        assertThat(reactionRepository.count()).isZero();
    }

    @Test
    void removesOwnReactionIdempotently() throws Exception {
        User sender = createActiveUser("sender@example.com", "Andrew");
        User receiver = createActiveUser("receiver@example.com", "Maria");
        reactionRepository.saveAndFlush(
                new Reaction(sender, receiver, ReactionType.PASS)
        );
        String accessToken = jwtTokenService.issue(sender);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete(
                                "/api/v1/users/{receiverId}/reaction",
                                receiver.getId()
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + accessToken
                            ))
                    .andExpect(status().isNoContent());
        }

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
