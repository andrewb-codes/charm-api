package ru.andrewb.charm.api.user.admin;

import jakarta.validation.constraints.NotNull;
import ru.andrewb.charm.api.user.domain.AccountStatus;

public record AdminUserAccountStatusUpdateRequest(
        @NotNull
        AccountStatus status
) {
}
