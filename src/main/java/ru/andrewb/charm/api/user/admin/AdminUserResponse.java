package ru.andrewb.charm.api.user.admin;

import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.domain.UserRole;

import java.time.Instant;

public record AdminUserResponse (
        Long id,
        String email,
        UserRole role,
        ProfileStatus profileStatus,
        AccountStatus accountStatus,
        String firstName,
        String lastName,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static  AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getProfileStatus(),
                user.getAccountStatus(),
                user.getFirstName(),
                user.getLastName(),
                user.getVersion(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
