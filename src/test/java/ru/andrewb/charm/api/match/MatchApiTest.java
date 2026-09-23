package ru.andrewb.charm.api.match;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import ru.andrewb.charm.api.ApiIntegrationTest;
import ru.andrewb.charm.api.reaction.Reaction;
import ru.andrewb.charm.api.reaction.ReactionRepository;
import ru.andrewb.charm.api.reaction.ReactionType;
import ru.andrewb.charm.api.security.access.JwtTokenService;
import ru.andrewb.charm.api.user.domain.Gender;
import ru.andrewb.charm.api.user.domain.User;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class MatchApiTest extends ApiIntegrationTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ReactionRepository reactionRepository;

    @Test
    void returnsOnlyMutualLikes() throws Exception {
        User andrew = createActiveUser(
                "andrew@example.com",
                "Andrew"
        );

        User maria = createActiveUser(
                "maria@example.com",
                "Maria"
        );

        User anna = createActiveUser(
                "anna@example.com",
                "Anna"
        );

        reactionRepository.saveAndFlush(
                new Reaction(andrew, maria, ReactionType.LIKE)
        );

        reactionRepository.saveAndFlush(
                new Reaction(maria, andrew, ReactionType.LIKE)
        );

        reactionRepository.saveAndFlush(
                new Reaction(andrew, anna, ReactionType.LIKE)
        );

        String accessToken = jwtTokenService.issue(andrew);

        mockMvc.perform(get("/api/v1/me/matches")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id")
                        .value(maria.getId()))
                .andExpect(jsonPath("$[0].firstName")
                        .value("Maria"))
                .andExpect(jsonPath("$[0].email")
                        .doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash")
                        .doesNotExist())
                .andExpect(jsonPath("$[0].status")
                        .doesNotExist());
    }

    @Test
    void rejectsMatchesRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/me/matches"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMatchesForPendingCurrentUser() throws Exception {
        User currentUser = userRepository.saveAndFlush(
                new User("andrew@example.com", "encoded-password")
        );
        String accessToken = jwtTokenService.issue(currentUser);

        mockMvc.perform(get("/api/v1/me/matches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Profile is incomplete"));
    }

    @Test
    void rejectsMatchesForBlockedCurrentUser() throws Exception {
        User currentUser = createActiveUser("andrew@example.com", "Andrew");
        String accessToken = jwtTokenService.issue(currentUser);
        currentUser.block();
        userRepository.saveAndFlush(currentUser);

        mockMvc.perform(get("/api/v1/me/matches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Account is blocked"));
    }

    @Test
    void excludesPendingAndBlockedUsersFromMatches() throws Exception {
        User currentUser = createActiveUser("current@example.com", "Current");
        User available = createActiveUser("available@example.com", "Available");
        User pending = userRepository.saveAndFlush(
                new User("pending@example.com", "encoded-password")
        );
        User blocked = createActiveUser("blocked@example.com", "Blocked");

        createMutualLike(currentUser, available);
        createMutualLike(currentUser, pending);
        createMutualLike(currentUser, blocked);

        blocked.block();
        userRepository.saveAndFlush(blocked);

        String accessToken = jwtTokenService.issue(currentUser);

        mockMvc.perform(get("/api/v1/me/matches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(available.getId()));
    }

    private void createMutualLike(User first, User second) {
        reactionRepository.saveAndFlush(
                new Reaction(first, second, ReactionType.LIKE)
        );
        reactionRepository.saveAndFlush(
                new Reaction(second, first, ReactionType.LIKE)
        );
    }

    private User createActiveUser(
            String email,
            String firstName
    ) {
        User user = new User(
                email,
                "encoded-password"
        );

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
