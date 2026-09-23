package ru.andrewb.charm.api.user.profile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.andrewb.charm.api.ApiIntegrationTest;
import ru.andrewb.charm.api.security.access.JwtTokenService;
import ru.andrewb.charm.api.user.domain.Gender;
import ru.andrewb.charm.api.user.domain.User;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicUserApiTest extends ApiIntegrationTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void doesNotReturnPendingUserPublicly() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("andrew@example.com", "encoded-password")
        );

        mockMvc.perform(get("/api/v1/users/{id}", user.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returnsActiveUserPublicly() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("andrew@example.com", "encoded-password")
        );
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(put("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Andrew",
                                  "lastName": "Brown",
                                  "birthDate": "2000-05-10",
                                  "gender": "MALE",
                                  "bio": "Java developer",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/users/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.firstName").value("Andrew"))
                .andExpect(jsonPath("$.lastName").value("Brown"))
                .andExpect(jsonPath("$.birthDate").value("2000-05-10"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.bio").value("Java developer"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.profileStatus").doesNotExist())
                .andExpect(jsonPath("$.accountStatus").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void doesNotReturnBlockedUserPublicly() throws Exception {
        User user = new User("andrew@example.com", "encoded-password");
        user.updateProfile(
                "Andrew",
                "Brown",
                LocalDate.of(2000, 5, 10),
                Gender.MALE,
                null
        );
        user.block();
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/users/{id}", user.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
    }

    @Test
    void returnsNotFoundForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
