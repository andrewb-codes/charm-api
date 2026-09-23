package ru.andrewb.charm.api.user.registration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.andrewb.charm.api.ApiIntegrationTest;
import ru.andrewb.charm.api.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserRegistrationApiTest extends ApiIntegrationTest {

    @Test
    void registersUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "Andrew@Example.com",
                                  "password": "secret-password"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber());

        User saved = userRepository.findByEmail("andrew@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotNull();
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret-password");
    }

    @Test
    void rejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void returnsConflictForDuplicateEmail() throws Exception {
        String firstRequest = """
                {
                  "email": "andrew@example.com",
                  "password": "first-password"
                }
                """;
        String duplicateRequest = """
                {
                  "email": "ANDREW@example.com",
                  "password": "second-password"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already exists"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
