package ru.andrewb.charm.api.user.profile;

public class UserVersionConflictException extends RuntimeException {

    public UserVersionConflictException(
            Long userId,
            long expectedVersion,
            long actualVersion
    ) {
        super(
                "User '%d' has version %d, but request contains version %d"
                        .formatted(userId, actualVersion, expectedVersion)
        );
    }
}
