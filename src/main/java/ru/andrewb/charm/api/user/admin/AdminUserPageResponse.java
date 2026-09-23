package ru.andrewb.charm.api.user.admin;

import java.util.List;

public record AdminUserPageResponse (
        List<AdminUserResponse> users,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
