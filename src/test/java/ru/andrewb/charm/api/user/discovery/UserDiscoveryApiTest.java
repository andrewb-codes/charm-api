package ru.andrewb.charm.api.user.discovery;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserDiscoveryApiTest extends ApiIntegrationTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ReactionRepository reactionRepository;

    @Test
    void returnsOnlyAvailableCandidates() throws Exception {
        User currentUser = createActiveUser(
                "current@example.com",
                "Andrew"
        );

        User available = createActiveUser(
                "available@example.com",
                "Maria"
        );

        User alreadyViewed = createActiveUser(
                "liked@example.com",
                "Anna"
        );

        User pending = new User(
                "pending@example.com",
                "encoded-password"
        );

        userRepository.saveAndFlush(pending);

        User blocked = createActiveUser(
                "blocked@example.com",
                "Blocked"
        );
        blocked.block();
        userRepository.saveAndFlush(blocked);

        reactionRepository.saveAndFlush(
                new Reaction(
                        currentUser,
                        alreadyViewed,
                        ReactionType.PASS
                )
        );

        String accessToken = jwtTokenService.issue(currentUser);

        mockMvc.perform(get("/api/v1/me/discovery")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()")
                        .value(1))
                .andExpect(jsonPath("$.users[0].id")
                        .value(available.getId()))
                .andExpect(jsonPath("$.users[0].firstName")
                        .value("Maria"))
                .andExpect(jsonPath("$.users[0].email")
                        .doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext")
                        .value(false));
    }

    @Test
    void paginatesDiscoveryUsers() throws Exception {
        User currentUser = createActiveUser(
                "current@example.com",
                "Andrew"
        );

        createActiveUser("first@example.com", "First");
        createActiveUser("second@example.com", "Second");
        createActiveUser("third@example.com", "Third");

        String accessToken = jwtTokenService.issue(currentUser);

        mockMvc.perform(get("/api/v1/me/discovery")
                        .param("page", "0")
                        .param("size", "2")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()")
                        .value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasNext")
                        .value(true));
    }

    @Test
    void rejectsDiscoveryWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/me/discovery"))
                .andExpect(status().isUnauthorized());
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
