package ru.andrewb.charm.api.user.profile;

import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.Gender;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.domain.UserRole;

import java.time.Instant;
import java.time.LocalDate;

public record CurrentUserResponse(
        Long id,
        String email,
        UserRole role,
        ProfileStatus profileStatus,
        AccountStatus accountStatus,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Gender gender,
        String bio,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getProfileStatus(),
                user.getAccountStatus(),
                user.getFirstName(),
                user.getLastName(),
                user.getBirthDate(),
                user.getGender(),
                user.getBio(),
                user.getVersion(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
