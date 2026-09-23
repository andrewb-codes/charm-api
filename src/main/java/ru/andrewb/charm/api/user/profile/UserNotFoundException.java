package ru.andrewb.charm.api.user.profile;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long id) {
        super("User with id '%d' was not found".formatted(id));
    }
}
