package ru.andrewb.charm.api.user.profile;

import jakarta.validation.constraints.*;
import ru.andrewb.charm.api.user.domain.Gender;

import java.time.LocalDate;

public record UserProfileUpdateRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in the past")
        LocalDate birthDate,

        @NotNull(message = "Gender is required")
        Gender gender,

        @Size(max = 1000, message = "Bio must not exceed 1000 characters")
        String bio,

        @NotNull(message = "Version is required")
        @PositiveOrZero(message = "Version must not be negative")
        Long version
) {
}
