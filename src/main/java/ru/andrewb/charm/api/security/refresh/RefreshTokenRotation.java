package ru.andrewb.charm.api.security.refresh;

import ru.andrewb.charm.api.user.domain.User;

public record RefreshTokenRotation(
        User user,
        String refreshToken
) {
}
