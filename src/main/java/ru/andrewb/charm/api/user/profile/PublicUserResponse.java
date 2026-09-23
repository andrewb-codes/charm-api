package ru.andrewb.charm.api.user.profile;

import ru.andrewb.charm.api.user.domain.Gender;
import ru.andrewb.charm.api.user.domain.User;

import java.time.LocalDate;

public record PublicUserResponse(
        Long id,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Gender gender,
        String bio
) {

    public static PublicUserResponse from(User user) {
        return new PublicUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getBirthDate(),
                user.getGender(),
                user.getBio()
        );
    }
}
