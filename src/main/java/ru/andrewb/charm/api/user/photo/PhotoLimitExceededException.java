package ru.andrewb.charm.api.user.photo;

public class PhotoLimitExceededException extends RuntimeException {

    public PhotoLimitExceededException(int limit) {
        super("User cannot have more than " + limit + " photos");
    }
}