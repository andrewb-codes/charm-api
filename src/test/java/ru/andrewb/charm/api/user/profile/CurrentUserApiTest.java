package ru.andrewb.charm.api.user.profile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.andrewb.charm.api.ApiIntegrationTest;
import ru.andrewb.charm.api.security.access.JwtTokenService;
import ru.andrewb.charm.api.user.domain.User;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CurrentUserApiTest extends ApiIntegrationTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void rejectsMeRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsCurrentUserFromJwtSubject() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("andrew@example.com", "encoded-password")
        );
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("andrew@example.com"))
                .andExpect(jsonPath("$.profileStatus").value("PENDING"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void updatesCurrentUserProfile() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("andrew@example.com", "encoded-password")
        );
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(put("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Andrew"))
                .andExpect(jsonPath("$.lastName").value("Brown"))
                .andExpect(jsonPath("$.bio").value("Java developer"))
                .andExpect(jsonPath("$.profileStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void rejectsStaleProfileVersion() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("andrew@example.com", "encoded-password")
        );
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(put("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("User version conflict"));
    }

    private String profileRequest() {
        return """
                {
                  "firstName": " Andrew ",
                  "lastName": " Brown ",
                  "birthDate": "2000-05-10",
                  "gender": "MALE",
                  "bio": " Java developer ",
                  "version": 0
                }
                """;
    }
}
