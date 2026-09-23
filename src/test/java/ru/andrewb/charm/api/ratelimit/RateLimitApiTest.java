package ru.andrewb.charm.api.ratelimit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.andrewb.charm.api.ApiIntegrationTest;
import ru.andrewb.charm.api.security.refresh.RefreshTokenRepository;
import ru.andrewb.charm.api.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
        "app.rate-limit.login.capacity=2",
        "app.rate-limit.login.refill-period=1h",
        "app.rate-limit.registration.capacity=2",
        "app.rate-limit.registration.refill-period=1h",
        "app.rate-limit.refresh.capacity=2",
        "app.rate-limit.refresh.refill-period=1h"
})
class RateLimitApiTest extends ApiIntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void rejectsExcessFailedLoginsAndIgnoresSpoofedForwardedIp() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(login("192.0.2.10")).andExpect(status().isUnauthorized());
        }
        var result = mockMvc.perform(login("192.0.2.10")
                        .header("X-Forwarded-For", "192.0.2.99"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too many requests"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"))
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        assertThat(Long.parseLong(result.getResponse().getHeader("Retry-After")))
                .isBetween(1L, 1800L);

        mockMvc.perform(login("192.0.2.11")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/health").with(request -> {
            request.setRemoteAddr("192.0.2.10");
            return request;
        })).andExpect(status().isOk());
    }

    @Test
    void successfulLoginsAlsoConsumeLimitWithoutIssuingTokensOnRejection() throws Exception {
        User user = userRepository.saveAndFlush(new User(
                "limit@example.com", passwordEncoder.encode("secret-password")));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(login("192.0.2.12")).andExpect(status().isOk());
        }
        mockMvc.perform(login("192.0.2.12")).andExpect(status().isTooManyRequests());
        assertThat(refreshTokenRepository.findAllByUser_Id(user.getId())).hasSize(2);
    }

    @Test
    void registrationHasOwnBudgetAndRejectedRequestCreatesNoUser() throws Exception {
        String ip = "192.0.2.13";
        mockMvc.perform(registration(ip, "invalid-email"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(registration(ip, "registered@example.com"))
                .andExpect(status().isCreated());
        var rejected = mockMvc.perform(registration(ip, "not-created@example.com"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail")
                        .value("Registration request rate limit exceeded"))
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        assertThat(userRepository.findByEmail("not-created@example.com")).isEmpty();
        assertThat(Long.parseLong(rejected.getResponse().getHeader("Retry-After")))
                .isPositive();
        // Registration exhaustion does not spend the login budget for this IP.
        mockMvc.perform(login(ip)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshHasOwnBudgetAndRejectedRequestDoesNotRotateToken() throws Exception {
        User user = userRepository.saveAndFlush(new User(
                "limit@example.com", passwordEncoder.encode("secret-password")));
        String ip = "192.0.2.14";

        var loginResult = mockMvc.perform(login(ip))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(loginResult.getResponse().getContentAsString(),
                "$.refreshToken");

        mockMvc.perform(refresh(ip, "invalid-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(refresh(ip, token))
                .andExpect(status().isOk());
        mockMvc.perform(refresh(ip, "another-invalid-token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail")
                        .value("Refresh request rate limit exceeded"))
                .andExpect(header().exists("Retry-After"));

        assertThat(refreshTokenRepository.findAllByUser_Id(user.getId())).hasSize(2);
        // The login budget is independent and still has its second attempt.
        mockMvc.perform(login(ip)).andExpect(status().isOk());
    }

    @Test
    void getUsersDoesNotConsumeRegistrationBudget() throws Exception {
        String ip = "192.0.2.15";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/users/999999")
                            .with(request -> {
                                request.setRemoteAddr(ip);
                                return request;
                            }))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(registration(ip, "fresh@example.com"))
                .andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder login(String ip) {
        return post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"limit@example.com","password":"secret-password"}
                        """);
    }

    private MockHttpServletRequestBuilder registration(String ip, String email) {
        return post("/api/v1/users")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"secret-password"}
                        """.formatted(email));
    }

    private MockHttpServletRequestBuilder refresh(String ip, String token) {
        return post("/api/v1/auth/refresh")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(token));
    }
}
