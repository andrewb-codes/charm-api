package ru.andrewb.charm.api.user.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.andrewb.charm.api.ApiIntegrationTest;
import ru.andrewb.charm.api.security.access.JwtTokenService;
import ru.andrewb.charm.api.security.refresh.RefreshTokenRepository;
import ru.andrewb.charm.api.security.refresh.RefreshTokenService;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.Gender;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.domain.UserRole;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserApiTest extends ApiIntegrationTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRegularUser() throws Exception {
        User user = saveUser("user@example.com", UserRole.USER);
        String accessToken = jwtTokenService.issue(user);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsUsersForAdminWithoutExposingPasswordHash() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        User regularUser = saveUser("user@example.com", UserRole.USER);
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].id").value(admin.getId()))
                .andExpect(jsonPath("$.users[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$.users[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.users[0].profileStatus").value("PENDING"))
                .andExpect(jsonPath("$.users[0].accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.users[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.users[1].id").value(regularUser.getId()))
                .andExpect(jsonPath("$.users[1].email").value("user@example.com"))
                .andExpect(jsonPath("$.users[1].role").value("USER"))
                .andExpect(jsonPath("$.users[1].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void returnsRequestedPageInIdOrder() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        saveUser("first@example.com", UserRole.USER);
        User second = saveUser("second@example.com", UserRole.USER);
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.users[0].id").value(second.getId()))
                .andExpect(jsonPath("$.users[0].email").value("second@example.com"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void filtersUsersByEmailIgnoringCaseAndSurroundingSpaces() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        User target = saveUser("target.person@example.com", UserRole.USER);
        saveUser("another@example.com", UserRole.USER);
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("email", "  TARGET.PERSON  ")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.users[0].id").value(target.getId()))
                .andExpect(jsonPath("$.users[0].email")
                        .value("target.person@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void combinesRoleProfileStatusAndAccountStatusFilters() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);

        User matchingUser = saveActiveUser("matching@example.com");
        matchingUser.block();
        userRepository.saveAndFlush(matchingUser);

        User pendingBlockedUser = saveUser("pending@example.com", UserRole.USER);
        pendingBlockedUser.block();
        userRepository.saveAndFlush(pendingBlockedUser);

        saveActiveUser("active@example.com");
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("role", "USER")
                        .param("profileStatus", "ACTIVE")
                        .param("accountStatus", "BLOCKED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.users[0].id").value(matchingUser.getId()))
                .andExpect(jsonPath("$.users[0].role").value("USER"))
                .andExpect(jsonPath("$.users[0].profileStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.users[0].accountStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void blocksActiveUserAndRevokesAllRefreshTokens() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        User target = saveActiveUser("user@example.com");
        refreshTokenService.issue(target);
        refreshTokenService.issue(target);
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(put("/api/v1/admin/users/{userId}/account-status", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("BLOCKED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId()))
                .andExpect(jsonPath("$.profileStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.accountStatus").value("BLOCKED"));

        User blocked = userRepository.findById(target.getId()).orElseThrow();
        assertThat(blocked.getProfileStatus()).isEqualTo(ProfileStatus.ACTIVE);
        assertThat(blocked.getAccountStatus()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(refreshTokenRepository.findAllByUser_Id(target.getId()))
                .hasSize(2)
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    @Test
    void unblocksBlockedUser() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        User target = saveActiveUser("user@example.com");
        target.block();
        userRepository.saveAndFlush(target);
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(put("/api/v1/admin/users/{userId}/account-status", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId()))
                .andExpect(jsonPath("$.profileStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        User unblocked = userRepository.findById(target.getId()).orElseThrow();
        assertThat(unblocked.getProfileStatus()).isEqualTo(ProfileStatus.ACTIVE);
        assertThat(unblocked.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void rejectsStatusChangeByRegularUser() throws Exception {
        User regularUser = saveActiveUser("regular@example.com");
        User target = saveActiveUser("target@example.com");
        String accessToken = jwtTokenService.issue(regularUser);

        mockMvc.perform(put("/api/v1/admin/users/{userId}/account-status", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("BLOCKED")))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(target.getId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void rejectsSelfAccountStatusChange() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(put("/api/v1/admin/users/{userId}/account-status", admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("BLOCKED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Self account status change is not allowed"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void rejectsChangingAnotherAdministratorAccountStatus() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        User targetAdmin = saveUser("target-admin@example.com", UserRole.ADMIN);
        refreshTokenService.issue(targetAdmin);
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(put("/api/v1/admin/users/{userId}/account-status", targetAdmin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("BLOCKED")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title")
                        .value("Administrator account modification is not allowed"));

        User unchangedAdmin = userRepository.findById(targetAdmin.getId()).orElseThrow();
        assertThat(unchangedAdmin.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(refreshTokenRepository.findAllByUser_Id(targetAdmin.getId()))
                .hasSize(1)
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNull());
    }

    @Test
    void blocksPendingUserWithoutChangingProfileStatus() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        User pendingUser = saveUser("pending@example.com", UserRole.USER);
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(put("/api/v1/admin/users/{userId}/account-status", pendingUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("BLOCKED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileStatus").value("PENDING"))
                .andExpect(jsonPath("$.accountStatus").value("BLOCKED"));

        User blocked = userRepository.findById(pendingUser.getId()).orElseThrow();
        assertThat(blocked.getProfileStatus()).isEqualTo(ProfileStatus.PENDING);
        assertThat(blocked.getAccountStatus()).isEqualTo(AccountStatus.BLOCKED);
    }

    @Test
    void unblocksPendingUserWithoutActivatingProfile() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        User pendingUser = saveUser("pending@example.com", UserRole.USER);
        pendingUser.block();
        userRepository.saveAndFlush(pendingUser);
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(put("/api/v1/admin/users/{userId}/account-status", pendingUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileStatus").value("PENDING"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        User unblocked = userRepository.findById(pendingUser.getId()).orElseThrow();
        assertThat(unblocked.getProfileStatus()).isEqualTo(ProfileStatus.PENDING);
        assertThat(unblocked.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void rejectsProfileStatusAsRequestedAccountStatus() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        User target = saveActiveUser("user@example.com");
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(put("/api/v1/admin/users/{userId}/account-status", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("PENDING")))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(target.getId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void returnsNotFoundForUnknownUser() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(put("/api/v1/admin/users/{userId}/account-status", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("BLOCKED")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
    }

    @Test
    void rejectsMissingStatus() throws Exception {
        User admin = saveUser("admin@example.com", UserRole.ADMIN);
        User target = saveActiveUser("user@example.com");
        String accessToken = jwtTokenService.issue(admin);

        mockMvc.perform(put("/api/v1/admin/users/{userId}/account-status", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"));
    }

    private User saveUser(String email, UserRole role) {
        User user = new User(email, "encoded-password");
        user.changeRole(role);

        return userRepository.saveAndFlush(user);
    }

    private User saveActiveUser(String email) {
        User user = new User(email, "encoded-password");
        user.updateProfile(
                "Test",
                "User",
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                null
        );

        return userRepository.saveAndFlush(user);
    }

    private String statusRequest(String status) {
        return """
                {
                  "status": "%s"
                }
                """.formatted(status);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
