package ru.andrewb.charm.api.user.registration;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("User with email '%s' already exists".formatted(email));
    }
}
