package ru.andrewb.charm.api.user.discovery;

import ru.andrewb.charm.api.user.profile.PublicUserResponse;

import java.util.List;

public record UserDiscoveryResponse(
        List<PublicUserResponse> users,
        int page,
        int size,
        boolean hasNext
) {
}
