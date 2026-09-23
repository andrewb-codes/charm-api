package ru.andrewb.charm.api.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.andrewb.charm.api.ApiIntegrationTest;
import ru.andrewb.charm.api.security.refresh.RefreshTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthApiTest extends ApiIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void logsInRegisteredUser() throws Exception {
        registerUser();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ANDREW@example.com",
                                  "password": "secret-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsIncorrectPassword() throws Exception {
        registerUser();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "andrew@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication failed"));
    }

    @Test
    void refreshesTokenPair() throws Exception {
        registerUser();

        String oldRefreshToken = loginAndGetRefreshToken();

        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "refreshToken": "%s"
                                    }
                                    """.formatted(oldRefreshToken))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();

        String newRefreshToken = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.refreshToken"
        );

        assertThat(newRefreshToken)
                .isNotEqualTo(oldRefreshToken);

        assertThat(refreshTokenRepository.count())
                .isEqualTo(2);
    }

    @Test
    void rejectsReusedRefreshToken() throws Exception {
        registerUser();

        String refreshToken = loginAndGetRefreshToken();

        String request = """
            {
              "refreshToken": "%s"
            }
            """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title")
                        .value("Refresh token rejected"))
                .andExpect(jsonPath("$.detail")
                        .value("Invalid or expired refresh token"));

        assertThat(refreshTokenRepository.count())
                .isEqualTo(2);
    }

    @Test
    void rejectsBlankRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "refreshToken": ""
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Request validation failed"));
    }

    @Test
    void logsOutAndRejectsRefreshAfterwards() throws Exception {
        registerUser();

        String refreshToken = loginAndGetRefreshToken();

        String request = """
            {
              "refreshToken": "%s"
            }
            """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title")
                        .value("Refresh token rejected"));

        assertThat(refreshTokenRepository.count())
                .isEqualTo(1);
    }

    @Test
    void logsOutIdempotently() throws Exception {
        registerUser();

        String refreshToken = loginAndGetRefreshToken();

        String request = """
            {
              "refreshToken": "%s"
            }
            """.formatted(refreshToken);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isNoContent());
        }

        assertThat(refreshTokenRepository.count())
                .isEqualTo(1);
    }

    @Test
    void acceptsLogoutWithUnknownRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "refreshToken": "unknown-refresh-token"
                            }
                            """))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.count())
                .isZero();
    }

    private void registerUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "andrew@example.com",
                                  "password": "secret-password"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private String loginAndGetRefreshToken() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email": "andrew@example.com",
                                      "password": "secret-password"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.refreshToken"
        );
    }
}
