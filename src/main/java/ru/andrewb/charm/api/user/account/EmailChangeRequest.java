package ru.andrewb.charm.api.user.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New email is required")
        @Email(message = "Email has invalid format")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String newEmail
) {
}
