package ru.andrewb.charm.api.user.admin;

import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.domain.UserRole;

public record AdminUserFilter(
        String email,
        UserRole role,
        ProfileStatus profileStatus,
        AccountStatus accountStatus
) {
}
